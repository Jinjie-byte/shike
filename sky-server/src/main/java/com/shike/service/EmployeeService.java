package com.shike.service;

import com.shike.dto.EmployeeDTO;
import com.shike.dto.EmployeeLoginDTO;
import com.shike.dto.EmployeePageQueryDTO;
import com.shike.dto.PasswordEditDTO;
import com.shike.entity.Employee;
import com.shike.result.PageResult;

public interface EmployeeService {

    /**
     * 员工登录
     * @param employeeLoginDTO
     * @return
     */
    Employee login(EmployeeLoginDTO employeeLoginDTO);

    void save(EmployeeDTO employeeDTO);

    PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    void startOrStop(Integer status, Long id);

    Employee getById(Long id);

    void update(EmployeeDTO employeeDTO);

    /**
     * 修改密码
     * @param passwordEditDTO
     */
    void editPassword(PasswordEditDTO passwordEditDTO);
}
