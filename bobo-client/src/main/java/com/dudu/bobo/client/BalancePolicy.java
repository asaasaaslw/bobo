package com.dudu.bobo.client;

/**
 * ���ؾ������
 * 
 * @author liangy43
 *
 */
public interface BalancePolicy {
	/**
	 * �����������뼯Ⱥ
	 */
	void join(RpcStub stub);

	/**
	 * 
	 */
	void remove(RpcStub stub);
	
	/**
	 * 
	 */
	RpcStub select();

	/**
	 * ����ָ�����
	 * 
	 */
	void doubt(RpcStub stub);

	/**
	 * ȡ��ָ���������
	 * 
	 */
	void unDoubt(RpcStub stub);
}
