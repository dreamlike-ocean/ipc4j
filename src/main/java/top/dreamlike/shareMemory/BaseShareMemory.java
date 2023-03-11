package top.dreamlike.shareMemory;

import top.dreamlike.WatchService;
import top.dreamlike.extension.ListExtension;
import top.dreamlike.helper.FileEvent;
import top.dreamlike.helper.NativeCallException;
import top.dreamlike.helper.NativeHelper;
import top.dreamlike.nativeLib.fcntl.fcntl_h;
import top.dreamlike.nativeLib.inotify.inotify_h;
import top.dreamlike.nativeLib.mman.mman_h;
import top.dreamlike.nativeLib.shm.shm;
import top.dreamlike.nativeLib.unistd.unistd_h;

import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.foreign.ValueLayout;
import java.util.List;

import static top.dreamlike.nativeLib.shm.shm.shm_open;

public class BaseShareMemory implements AutoCloseable {

    private final static int WRITE_FIELD_OFFSET = 0;

    private final static int READ_FIELD_OFFSET = 4;
    protected final String name;
    protected int shm_fd;
    //前4字节 写入了多少字节
    //4-8 读取了多少字节
    protected MemorySegment mmap_base;
    protected WatchService listener;
    protected int wid;
    protected int shm_size;
    protected int preRead;
    protected int preWrite;
    protected UnsafeApi api;

    public BaseShareMemory(String memoryName, int size, boolean isReader, boolean needPopulate) {
        try (MemorySession session = MemorySession.openConfined()) {
            this.name = memoryName;
            MemorySegment name = session.allocateUtf8String(memoryName);
            openShareMemory(name, isReader);
            allocateShareMemory(size);
            mapShareMemory(needPopulate);
            initListener(session);
            this.api = new UnsafeApi();
        }
    }


    public UnsafeApi unsafe() {
        return api;
    }

    private void openShareMemory(MemorySegment path, boolean isReader) {
        int excl_flag = isReader ? fcntl_h.O_EXCL() : 0;
        int res = shm_open(path, fcntl_h.O_RDWR() | fcntl_h.O_CREAT() | excl_flag, 0777);
        if (res == -1) {
            throw new NativeCallException(NativeHelper.getNowError());
        }
        this.shm_fd = res;
    }

    private void allocateShareMemory(int size) {
        int res = unistd_h.ftruncate(shm_fd, size);
        if (res == -1) {
            throw new NativeCallException(NativeHelper.getNowError());
        }
        this.shm_size = size;
    }

    private void mapShareMemory(boolean needPopulate) {
        int populate_flag = needPopulate ? mman_h.MAP_POPULATE() : 0;
        MemoryAddress memoryAddress = mman_h.mmap(MemoryAddress.NULL, this.shm_size, mman_h.PROT_WRITE() | mman_h.PROT_READ(), mman_h.MAP_SHARED() | populate_flag | mman_h.MAP_LOCKED(), shm_fd, 0);
        this.mmap_base = MemorySegment.ofAddress(memoryAddress, shm_size, MemorySession.global());
        mmap_base.set(ValueLayout.JAVA_INT, WRITE_FIELD_OFFSET, 0);
        mmap_base.set(ValueLayout.JAVA_INT, READ_FIELD_OFFSET, 0);
        this.preRead = this.preWrite = 0;
    }

    //todo jdk20 scopeLocal+new ffm api 重新实现，先传递下来再说
    private void initListener(MemorySession session) {
        this.listener = new WatchService();

        MemorySegment pathBuff = session.allocate(64);
        int pid = unistd_h.getpid();
        String fdsPath = String.format("/proc/%d/fd/", pid);
        long readLength = unistd_h.readlink(session.allocateUtf8String(fdsPath + shm_fd), pathBuff, pathBuff.byteSize());
        MemorySegment pathName = pathBuff.asSlice(0, readLength);
        this.wid = listener.register(pathName, inotify_h.IN_MODIFY());
    }

    /**
     * 注意 是否阻塞到返回取决于对 watchService的操作
     * 当被设置为非阻塞模式时 会cpu 100%注意使用
     */
    protected void waitNotify() {
        while (true) {
            List<FileEvent> events = listener.selectEvent();
            if (ListExtension.findAny(events, fe -> fe.wfd == this.wid) != null) {
                return;
            }
        }
    }

    protected boolean isReadModify() {
        // 4-8 读了多少
        int i = currentReadValue();
        if (i != preRead) {
            this.preRead = i;
            return true;
        }
        return false;
    }

    public int currentReadValue() {
        return mmap_base.get(ValueLayout.JAVA_INT, READ_FIELD_OFFSET);
    }

    protected boolean isWriteModify() {
        // 写了多少
        int i = currentWriteValue();
        if (i != preWrite) {
            this.preWrite = i;
            return true;
        }
        return false;
    }

    public int currentWriteValue() {
        return mmap_base.get(ValueLayout.JAVA_INT, WRITE_FIELD_OFFSET);
    }


    public MemorySegment shareMemory() {
        return mmap_base.asSlice(8);
    }


    @Override
    public void close() throws Exception {
        try (MemorySession session = MemorySession.openConfined()) {
            MemorySegment link = session.allocateUtf8String(name);
            shm.shm_unlike(link);
            mman_h.munmap(this.mmap_base, shm_size);
        } finally {
            listener.close();
        }
    }

    protected int modifyReadValue(int value) {
        long res = unistd_h.lseek(shm_fd, READ_FIELD_OFFSET, unistd_h.SEEK_SET());
        if (res == -1) {
            return NativeHelper.getErrorNo();
        }
        //直接写入mmap
        mmap_base.set(ValueLayout.JAVA_INT, READ_FIELD_OFFSET, value);
        //触发inotify事件
        return (int) unistd_h.write(shm_fd, mmap_base.asSlice(READ_FIELD_OFFSET), ValueLayout.JAVA_INT.byteSize());
    }


    protected int modifyWriteValue(int value) {
        long res = unistd_h.lseek(shm_fd, WRITE_FIELD_OFFSET, unistd_h.SEEK_SET());
        if (res == -1) {
            return NativeHelper.getErrorNo();
        }
        //直接写入mmap
        mmap_base.set(ValueLayout.JAVA_INT, WRITE_FIELD_OFFSET, value);
        //触发inotify事件
        return (int) unistd_h.write(shm_fd, mmap_base.asSlice(WRITE_FIELD_OFFSET), ValueLayout.JAVA_INT.byteSize());
    }

    public class UnsafeApi {
        public int inotifyFd() {
            return listener.fd();
        }

        public int shmFd() {
            return shm_fd;
        }
    }
}
