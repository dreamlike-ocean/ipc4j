# IPC4J 进程间内存共享

基于`inotify`,`mmap`和`shm_open`构建的半双工内存共享

大体思路

![image-20230311132412791](assets/twitter.png)

**feature：**

- [x] 共享内存
- [x] 支持事件通知机制且支持poll特性
- [x] 与Linux发行版无关
- [x] 支持改变大小
- [x] 分离读写端
- [x] 读写端切换
- [ ] ringbuffer实现