package com.stephan.tof.distributedLock;

import redis.clients.jedis.Jedis;

/**
 * 可以调用任何Jedis命令，具体命令由具体类实现 <br>
 * 
 * @author Stephan Gao
 * @param <T> 内部实现的返回值
 */
public interface JedisCommand<T> {

	/**
	 * 注意，由于容器来统一管理连接池，所以实现类不能存储jedis对象
	 * 
	 * @param jedis
	 * @return
	 * @throws Exception
	 */
	public T run(Jedis jedis) throws Exception;
	
}
