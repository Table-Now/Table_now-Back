package zerobase.tableNow.domain.store.controller.menu.entity;

import jakarta.persistence.*;
import lombok.*;
import zerobase.tableNow.domain.baseEntity.BaseEntity;
import zerobase.tableNow.domain.cart.entity.CartEntity;
import zerobase.tableNow.domain.constant.Status;
import zerobase.tableNow.domain.store.entity.StoreEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "menus")
public class MenuEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private StoreEntity storeId;

    private String image;
    private String name;
    private String price;

    @Enumerated(EnumType.STRING)
    private Status status;

    @OneToMany(mappedBy = "menuId", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @Builder.Default
    private List<CartEntity> carts = new ArrayList<>();

}