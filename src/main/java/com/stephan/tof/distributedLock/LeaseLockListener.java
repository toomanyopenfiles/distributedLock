package com.stephan.tof.distributedLock;

import com.stephan.tof.distributedLock.RedisLeaseLock.LockMessage;

/**
 * 当锁状态变更时需要回调的接口 </br>
 * 使用方式：</br>
 * 1. 实现此Listener接口，定义锁状态变更后需要做的操作 </br>
 * 2. 注册锁时，将listener传入到RedisLeaseLock中：{@link RedisLeaseLock#registerLock(String, String, LeaseLockListener)} </br>
 * 3. tryLock时如果发现锁状态变更，则会回调此listener </br>
 * </br>
 * 不同的锁可以实现不同的listener，每把锁会回调自己所属的listener对象
 * 
 * @author Stephan Gao
 * @since 2016年2月17日
 *
 */
public interface LeaseLockListener {

	public void lockChanged(LockMessage lock, boolean newStatus, int changeTimes);
	
}
