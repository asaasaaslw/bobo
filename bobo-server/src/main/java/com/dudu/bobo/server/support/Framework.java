package com.dudu.bobo.server.support;

import com.dudu.bobo.server.RpcSkeleton;

/**
 * 
 * @author liangy43
 *
 */
public class Framework {

	/**
	 * 
	 * @param service
	 * @param clazz
	 */
	public void export(Object service, Class<?> clazz) {
		if (clazz.isInstance(service) == false) {
			return;
		}
	
		// ���skeleton
		RpcSkeleton rpcSkeleton = new RpcSkeletonImpl(service, clazz);
		
		// ����
	}
	
	/**
	 * 
	 */
	public void main() {
		// ͨ��ģ��
		
		// ����ģ��
		
		//
	}
}
