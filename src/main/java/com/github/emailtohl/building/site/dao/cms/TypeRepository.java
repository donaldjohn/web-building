package com.github.emailtohl.building.site.dao.cms;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.github.emailtohl.building.site.entities.cms.Type;

/**
 * 文章类型的数据访问接口
 * @author HeLei
 * @date 2017.02.17
 */
public interface TypeRepository extends JpaRepository<Type, Long> {
	/**
	 * 根据类型名查询类型实体
	 * @param name
	 * @return
	 */
	Type findByName(String name);
	
	/**
	 * 分页查询文章类型
	 * @param name
	 * @param pageable
	 * @return
	 */
	Page<Type> findByNameLike(String name, Pageable pageable);
	
}
