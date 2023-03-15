package top.dreamlike.shareMemory;

import top.dreamlike.WatchService;

import java.lang.foreign.MemorySegment;
import java.util.function.ToIntFunction;

public class MemoryReader extends ResizableShareMemory {
    public MemoryReader(String memoryName, int size) {
        super(memoryName, size, true, true);
    }

    protected MemoryReader(String name, int shm_fd, MemorySegment mmap_base, WatchService listener, int wid, int shm_size, int preRead, int preWrite, boolean needPopulate) {
        super(name, shm_fd, mmap_base, listener, wid, shm_size, preRead, preWrite, needPopulate);
    }

    @Override
    public void waitNotify() {
        while (isReadModify()) {
            super.waitNotify();
        }
    }

    public void read(ToIntFunction<MemorySegment> readFn) {
        int readValue = readFn.applyAsInt(shareMemory());
        incrReadVersion(readValue);
    }

    public MemoryWriter transToWriter() {
        return new MemoryWriter(name, shm_fd, mmap_base, listener, wid, shm_size, preReadVersion, preWriteVersion, needPopulate);
    }
}
