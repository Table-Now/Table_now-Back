package zerobase.tableNow.domain.store.controller.menu.service;

import zerobase.tableNow.domain.constant.Status;
import zerobase.tableNow.domain.store.controller.menu.dto.MenuDto;
import zerobase.tableNow.domain.store.controller.menu.dto.MenuUpdateDto;

import java.util.List;

public interface MenuService {
    MenuDto register(MenuDto menuDto);

    List<MenuDto> list(Long storeId);

    void delete(Long id);

    void update(MenuUpdateDto menuUpdateDto);

    void reStatus(Long menuId);
}