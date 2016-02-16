# distributedLock简介

distributedLock是用java开发的租约锁客户端库，使用这个库，用户可以解决分布式系统内部的中心节点（如调度、控制节点）的单点问题，做到服务的高可用性、去中心化。
此库依赖Redis主从+Sentinel集群存储分布式锁，建议最小集群配置为两个Redis节点+三个Sentinel节点。

## 主要功能：
- 解决单点故障导致的中心节点不可用问题
- 解决由于网络分区导致的多个中心节点的脑裂问题
- tryLock过程不进行网络通讯，锁的获取和续租完全异步完成，用户调用不阻塞

## 使用示例
[RedisLeaseLockSample](./src/main/java/com/stephan/tof/distributedLock/RedisLeaseLockSample.java)

## 架构与设计
待完善

