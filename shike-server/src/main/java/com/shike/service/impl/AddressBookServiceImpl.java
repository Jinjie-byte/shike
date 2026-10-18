package com.shike.service.impl;

import com.shike.context.BaseContext;
import com.shike.entity.AddressBook;
import com.shike.exception.AddressBookBusinessException;
import com.shike.mapper.AddressBookMapper;
import com.shike.service.AddressBookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Slf4j
public class AddressBookServiceImpl implements AddressBookService {
    @Autowired
    private AddressBookMapper addressBookMapper;

    /**
     * 条件查询
     *
     * @param addressBook
     * @return
     */
    public List<AddressBook> list(AddressBook addressBook) {
        return addressBookMapper.list(addressBook);
    }

    /**
     * 新增地址
     *
     * @param addressBook
     */
    public void save(AddressBook addressBook) {
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setIsDefault(0);
        addressBookMapper.insert(addressBook);
    }

    /**
     * 根据id查询
     *
     * @param id
     * @return
     */
    public AddressBook getById(Long id) {
        return getOwnedAddress(id);
    }

    /**
     * 按 id 查地址，并校验归属当前登录用户。
     *
     * 为什么必须校验：接口只从 URL 拿 id，不校验的话任何登录用户改个 id 就能
     * 读到别人的收货人、手机号和门牌号 —— 典型越权（IDOR）。
     * 统一抛"地址不存在"，避免暴露"这个 id 是存在的"。
     */
    private AddressBook getOwnedAddress(Long id) {
        AddressBook addressBook = addressBookMapper.getById(id);
        Long userId = BaseContext.getCurrentId();
        if (addressBook == null || userId == null || !userId.equals(addressBook.getUserId())) {
            throw new AddressBookBusinessException("地址不存在");
        }
        return addressBook;
    }

    /**
     * 根据id修改地址
     *
     * @param addressBook
     */
    public void update(AddressBook addressBook) {
        // 先确认这条地址是自己的，否则可以构造请求改掉别人的收货地址
        getOwnedAddress(addressBook.getId());
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBookMapper.update(addressBook);
    }

    /**
     * 设置默认地址
     *
     * @param addressBook
     */
    @Transactional
    public void setDefault(AddressBook addressBook) {
        //1、将当前用户的所有地址修改为非默认地址 update address_book set is_default = ? where user_id = ?
        addressBook.setIsDefault(0);
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBookMapper.updateIsDefaultByUserId(addressBook);

        //2、将当前地址改为默认地址 update address_book set is_default = ? where id = ?
        addressBook.setIsDefault(1);
        addressBookMapper.update(addressBook);
    }

    /**
     * 根据id删除地址
     *
     * @param id
     */
    public void deleteById(Long id) {
        // 同上：只能删自己的地址
        getOwnedAddress(id);
        addressBookMapper.deleteById(id);
    }

}
