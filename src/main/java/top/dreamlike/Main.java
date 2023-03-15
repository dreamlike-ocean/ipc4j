package top.dreamlike;


import sun.misc.Unsafe;
import top.dreamlike.shareMemory.BaseShareMemory;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.UUID;

import static java.lang.foreign.ValueLayout.JAVA_INT;

public class Main {
    static boolean flag = true;

    public static void main(String[] args) throws IOException {

        BaseShareMemory memory = new BaseShareMemory("testShm", 4 * 1024, false, true);
        try (memory) {
            MemorySegment shareMemory = memory.shareMemory();
            var uuid = UUID.randomUUID().toString();
            System.out.println(uuid);
            shareMemory.setUtf8String(0, uuid);
            System.out.println(shareMemory.getUtf8String(0));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public static void cas(MemorySegment memorySegment) {
        VarHandle handle = JAVA_INT
                .varHandle();
        Object compareAndExchange = handle.compareAndExchangeRelease(memorySegment, 10, 1245);
        compareAndExchange = handle.compareAndExchangeRelease(memorySegment.asSlice(4), 10, 1245);
    }


    public static void unsafeCas(MemorySegment segment) throws NoSuchFieldException, IllegalAccessException {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);

        boolean b = unsafe.compareAndSwapInt(null, segment.address().toRawLongValue(), 10, 151);
    }

}