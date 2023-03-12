package top.dreamlike.shareMemory;

import top.dreamlike.WatchService;

import java.lang.foreign.MemorySegment;
import java.util.function.ToIntFunction;

public class MemoryWriter extends ResizableShareMemory {
    public MemoryWriter(String memoryName, int size) {
        super(memoryName, size, false, true);
    }

    protected MemoryWriter(String name, int shm_fd, MemorySegment mmap_base, WatchService listener, int wid, int shm_size, int preRead, int preWrite, boolean needPopulate) {
        super(name, shm_fd, mmap_base, listener, wid, shm_size, preRead, preWrite, needPopulate);
    }

    @Override
    public void waitNotify() {
        while (isWriteModify()) {
            super.waitNotify();
        }
    }

    public void write(ToIntFunction<MemorySegment> writeFn) {
        int writeValue = writeFn.applyAsInt(shareMemory());
        modifyWriteValue(writeValue);
    }

    public MemoryReader transTtoReader() {
        return new MemoryReader(name, shm_fd, mmap_base, listener, wid, shm_size, preRead, preWrite, needPopulate);
    }

}
