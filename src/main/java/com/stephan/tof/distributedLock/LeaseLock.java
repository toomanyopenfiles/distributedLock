/**
 * 
 */
package com.stephan.tof.distributedLock;

/**
 * 用Redis实现的无阻塞租约锁，抢到锁的节点会自动后台续租，没抢到锁的节点自动后台抢锁 <br>
 * 持有锁的节点的默认续租策略：每隔2s续租一次，租约周期20s（可以通过构造函数传入指定值以改变默认值）<br>
 * 也就是说如果连续10次续租失败，此锁会自动过期，其它节点才有机会重新开始竞争抢锁 <br> 
 * 
 * @author Stephan Gao
 * @email blueswind830630@gmail.com
 * @date 2016-02-08
 */
public interface LeaseLock {

	/**
	 * 注册一个自动租约锁，在程序初始化的时候注册一次就可以
	 * 
	 * @param key 锁的key
	 * @param value 锁的value，可以设置为本机IP或者IP+pid等可以唯一标识一个锁持有方的信息
	 * 
	 */
	public void registerLock(String key, String value, LeaseLockListener listener);
	
	/**
	 * 尝试获取锁
	 * 
	 * @return 如果当前节点目前持有锁则返回true，否则返回false
	 */
	public boolean tryLock(String key);
	
}
