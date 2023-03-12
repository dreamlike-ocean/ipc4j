package top.dreamlike.shareMemory;

import top.dreamlike.WatchService;
import top.dreamlike.helper.NativeCallException;
import top.dreamlike.helper.NativeHelper;
import top.dreamlike.nativeLib.mman.mman_h;
import top.dreamlike.nativeLib.unistd.unistd_h;

import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;

public class ResizableShareMemory extends BaseShareMemory {

    protected boolean needPopulate;

    private boolean allowResize = false;

    public ResizableShareMemory(String memoryName, int size, boolean isReader, boolean needPopulate) {
        super(memoryName, size, isReader, needPopulate);
        this.needPopulate = needPopulate;
    }

    protected ResizableShareMemory(String name, int shm_fd, MemorySegment mmap_base, WatchService listener, int wid, int shm_size, int preRead, int preWrite, boolean needPopulate) {
        super(name, shm_fd, mmap_base, listener, wid, shm_size, preRead, preWrite);
        this.needPopulate = needPopulate;
    }

    public void growSize(int newSize) {
        if (newSize < shm_size) {
            throw new IllegalArgumentException("new size should greater than current size");
        }
        resizeUnsafe(newSize);
    }


    //保护多个临界变量 且保证内存可见性
    //tood 考虑是否支持
    public void resizeUnsafe(int size) {
        if (!allowResize) {
            throw new IllegalArgumentException("Please set allowResize=true");
        }
        MemorySegment oldMmap = mmap_base;
        int oldSize = shm_size;
        int res = unistd_h.ftruncate(shm_fd, size);
        if (res == -1) {
            throw new NativeCallException(NativeHelper.getNowError());
        }
        int populate_flag = needPopulate ? mman_h.MAP_POPULATE() : 0;
        MemoryAddress memoryAddress = mman_h.mmap(MemoryAddress.NULL, size, mman_h.PROT_WRITE() | mman_h.PROT_READ(), mman_h.MAP_SHARED() | populate_flag | mman_h.MAP_LOCKED(), shm_fd, 0);
        synchronized (this) {
            //防止并发resize 导致重复munmap
            if (mmap_base != oldMmap) {
                oldMmap = null;
            }
            this.mmap_base = MemorySegment.ofAddress(memoryAddress, shm_size, MemorySession.global());
            this.shm_size = size;
        }
        if (oldMmap != null) {
            mman_h.munmap(oldMmap, oldSize);
        }
    }

}
