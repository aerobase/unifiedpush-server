package org.jboss.aerogear.unifiedpush.service;

import java.util.List;
import java.util.Map;

import org.jboss.aerogear.unifiedpush.api.PushApplication;

public interface CategoryDeploymentService {
	
	void deployCategories(PushApplication application, Map<String, List<String>> categoryData);

}