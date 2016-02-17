/**
 * 
 */
package com.stephan.tof.distributedLock;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * @author Stephan Gao
 * @email blueswind830630@gmail.com
 * @date 2016-02-08
 *
 */
public class RedisLeaseLock implements LeaseLock {

	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	private Thread tryLockThread = null;
	
	private final JedisSentinelPool pool;
	
	private final ConcurrentMap<String, LockMessage> lockMap = new ConcurrentHashMap<String, LockMessage>();
	
	private final long leaseExpireTime;	// 租约过期时长（单位秒）
	private static final long DEFAULT_LEASE_EXPIRE_TIME = 20;
	
	private final long leasePeriod;		// 续租间隔（单位秒）
	private static final long DEFAULT_LEASE_PERIOD = 2;
	
	public RedisLeaseLock(JedisSentinelPool pool) {
		this(pool, DEFAULT_LEASE_PERIOD, DEFAULT_LEASE_EXPIRE_TIME);
	}
	
	/**
	 * 初始化全局配置，设置续租间隔leasePeriod 和租约过期时长leaseExpireTime，单位秒 </br>
	 * 注意：续租间隔应该远小于租约过期时长，这样才能保证在偶尔续租失败的情况下，锁不会被抢走 </br>
	 * 
	 * @param leasePeriod 续租间隔，默认2s（单位秒）
	 * @param leaseExpireTime 租约过期时长，默认20s（单位秒） 
	 */
	public RedisLeaseLock(JedisSentinelPool pool, long leasePeriod, long leaseExpireTime) {
		this.pool = pool;
		
		// 租约过期时长 至少 是续租间隔的4倍，否则抛出异常
		if (leaseExpireTime <= leasePeriod * 4) {
			throw new IllegalArgumentException("leaseExpireTime is too small, it must great than " + (leasePeriod * 4));
		}
		this.leasePeriod = leasePeriod;
		this.leaseExpireTime = leaseExpireTime;
	}
	
	@Override
	public synchronized void registerLock(String key, String value, LeaseLockListener listener) {
		if (lockMap.containsKey(key)) {
			logger.error("register lease lock error, key is exist! key=" + key);
			return;
		}
		
		lockMap.put(key, new LockMessage(key, value, listener));
		logger.info("register lease lock, key=" + key + ", value=" + value);

		// 注册时需要阻塞调用一次
		setLeaseLock();
		if (tryLockThread == null) {
			initTryLockThread();
		}
	}

	private void initTryLockThread() {
		this.tryLockThread = new Thread(new Runnable() {
			@Override
			public void run() {
				while(true) {
					try {
						Thread.sleep(leasePeriod * 1000L);
					} catch (InterruptedException e) {
						logger.error(e.getMessage(), e);
					}
					
					setLeaseLock();
				}
			}
			
		}, "Redis-LeaseLock-Thread");
		
		tryLockThread.setDaemon(true);
		tryLockThread.start();
	}
	
	private void setLeaseLock() {
		for (final LockMessage lockMessage : lockMap.values()) {
			try {
				// 锁已过期，尝试nx操作获取锁
				if (System.currentTimeMillis() >= lockMessage.getExpireTime()) {
					String isOk = doCommand(new JedisCommand<String>() {
						@Override
						public String run(Jedis jedis) throws Exception {
							return jedis.set(lockMessage.getKey(), lockMessage.getValue(), "NX", "EX", leaseExpireTime);
						}
					});
					
					if (isOk != null && isOk.equals("OK")) {
						lockMessage.setExpireTime(
								System.currentTimeMillis() + (leaseExpireTime * 1000L));
					}
					// 如果NX不能获取锁，需要进一步确认此锁是否还属于自己
					// 如果属于自己，则取出当前锁的TTL赋给内存对象
					else if (isOk == null) {
						String serverLockMessage = doCommand(new JedisCommand<String>() {
							@Override
							public String run(Jedis jedis) throws Exception {
								return jedis.get(lockMessage.getKey());
							}
						});
						
						if (serverLockMessage != null && serverLockMessage.equals(lockMessage.getValue())) {
							long ttl = doCommand(new JedisCommand<Long>() {
								@Override
								public Long run(Jedis jedis) throws Exception {
									return jedis.ttl(lockMessage.getKey());
								}
							});
							
							if (ttl > leasePeriod) {
								lockMessage.setExpireTime(
										System.currentTimeMillis() + (ttl * 1000L));
							}
						}
					}
				} 
				// 锁还未过期，尝试expire续租锁
				else {
					// 为了防止极端情况下锁正好过期 or 锁被其它节点抢走，需要确认锁是自己拥有的
					// 如果锁不属于自己，则放弃续约
					String serverLockMessage = doCommand(new JedisCommand<String>() {
						@Override
						public String run(Jedis jedis) throws Exception {
							return jedis.get(lockMessage.getKey());
						}
					});
					if (serverLockMessage == null || !serverLockMessage.equals(lockMessage.getValue())) {
						return;
					}
					
					long isOk = doCommand(new JedisCommand<Long>() {
						@Override
						public Long run(Jedis jedis) throws Exception {
							return jedis.expire(lockMessage.getKey(), (int)leaseExpireTime);
						}
					});
					if (isOk == 1L) {
						lockMessage.setExpireTime(
								System.currentTimeMillis() + (leaseExpireTime * 1000L));
					}
				}
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}
	}
	
	@Override
	public boolean tryLock(String key) {
		LockMessage lockMessage = checkLockExist(key);
		if (System.currentTimeMillis() < lockMessage.getExpireTime()) {
			lockMessage.setNewStatus(true);
			return true;
		} else{
			lockMessage.setNewStatus(false);
			return false;
		}
	}
	
	private <T> T doCommand(JedisCommand<T> jedisCommand) throws Exception {
		T result = null;
		Jedis jedis = pool.getResource();
		try {
			result = jedisCommand.run(jedis);
		} catch(JedisConnectionException e) {
			Jedis brokenJedis = jedis;
			jedis = null;
			pool.returnBrokenResource(brokenJedis);
			throw e;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
			}
		}
		
		return result;
	}

	private LockMessage checkLockExist(String key) {
		LockMessage lockMessage = null;
		if ((lockMessage = lockMap.get(key)) == null) {
			throw new IllegalArgumentException("RedisLeaseLock not found! key=" + key);
		}
		return lockMessage;
	}
	
	class LockMessage {
		
		private final String key;
		
		private final String value;
		
		private final AtomicLong expireTime = new AtomicLong(0L);
		
		private final LeaseLockListener listener;
		
		private final AtomicBoolean lockStatus = new AtomicBoolean(false);
		
		private final AtomicInteger changeTimes = new AtomicInteger(0);
		
		private LockMessage(String key, String value, LeaseLockListener listener) {
			this.key = key;
			this.value = value;
			this.listener = listener;
		}

		public String getKey() {
			return key;
		}

		public String getValue() {
			return value;
		}
		
		private boolean setNewStatus(boolean newStatus) {
			boolean success = lockStatus.compareAndSet(!newStatus, newStatus);
			if (success && listener != null) {
				listener.lockChanged(this, newStatus, changeTimes.incrementAndGet());
			}
			
			return success;
		}

		/**
		 * 锁过期时间 <br>
		 * 如果当前节点持有锁，则expireTime和redis中的锁过期时间保持一致 <br>
		 * 如果当前节点不持有锁，则expireTime是当前节点最后一次获取到锁的过期时间 <br>
		 */
		public long getExpireTime() {
			return expireTime.get();
		}

		private void setExpireTime(long expireTime) {
			this.expireTime.set(expireTime);
		}
		
	}

}
