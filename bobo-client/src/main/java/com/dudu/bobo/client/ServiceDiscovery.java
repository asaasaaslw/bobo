package com.dudu.bobo.client;

import java.util.List;

import com.dudu.bobo.common.ServiceInfo;

/**
 * ������
 * 
 * @author liangy43
 *
 */
public interface ServiceDiscovery {

	/**
	 * 
	 */
	List<ServiceInfo>	query();
	
	/**
	 * 
	 */
	void onServiceListener(ServiceEvent event);
}
