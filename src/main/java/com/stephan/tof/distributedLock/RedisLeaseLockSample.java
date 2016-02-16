package com.stephan.tof.distributedLock;

import java.util.HashSet;
import java.util.Set;

import redis.clients.jedis.JedisSentinelPool;

/**
 * 
 * @author Stephan Gao
 * @since 2016年2月1日
 *
 */
public class RedisLeaseLockSample {

	private static final String LOCK1 = "lock1";
	private static final String LOCK2 = "lock2";
	
	private static RedisLeaseLock lock = null;
	
	public static void main(String[] args) {
		init();
		doSomething1();
		doSomething2();
	}
	
	/**
	 * 容器初始化时需要的代码，只执行一次
	 */
	private static void init() {
		// 指定masterName和sentinel nodes，注意masterName必须和sentinel中配置的master name一致
		// redis sentinel文档请参考：http://redis.io/topics/sentinel#configuring-sentinel
		String masterName = "xxx";
		Set<String> sentinels = new HashSet<String>();
		sentinels.add("127.0.0.1:8880");
		sentinels.add("127.0.0.1:8881");
		sentinels.add("127.0.0.1:8882");
		JedisSentinelPool pool = new JedisSentinelPool(masterName, sentinels);
		
		// 根据业务需要，声明并注册多把锁
		// 在同一时间，可以保证每把锁有且仅有一个业务节点能够获取到
		lock = new RedisLeaseLock(pool);
		lock.registerLock(LOCK1, getNodeId());
		lock.registerLock(LOCK2, getNodeId());
	}
	
	/**
	 * 业务逻辑1
	 */
	private static void doSomething1() {
		if (lock.tryLock(LOCK1)) {
			// 取到LOCK1的锁，进入业务代码1
		}
	}
	
	/**
	 * 业务逻辑2
	 */
	private static void doSomething2() {
		if (lock.tryLock(LOCK2)) {
			// 取到LOCK2的锁，进入业务代码2
		}
	}
	
	/**
	 * 返回当前应用进程的实例ip，或者ip:pid，不同的应用进程应该返回不同的值，以便于区分当前锁是由哪个进程持有的
	 * 
	 * @return
	 */
	private static String getNodeId() {
		return null;
	}

}
