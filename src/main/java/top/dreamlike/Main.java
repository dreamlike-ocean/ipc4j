package top.dreamlike;


import top.dreamlike.shareMemory.BaseShareMemory;

import java.lang.foreign.MemorySegment;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
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

}