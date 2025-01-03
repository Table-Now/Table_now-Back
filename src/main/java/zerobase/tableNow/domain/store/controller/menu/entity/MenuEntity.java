package zerobase.tableNow.domain.store.controller.menu.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private StoreEntity store;

    private String image;
    @NotBlank(message = "메뉴 이름 필수 입력.")
    private String name;

    @NotNull(message = "금액 필수 입력.")
    @Min(value = 100, message = "100원 이상이어야 합니다.")
    private int price;

    @Enumerated(EnumType.STRING)
    private Status status;

    private int count; //개수

    @OneToMany(mappedBy = "menu", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @Builder.Default
    private List<CartEntity> carts = new ArrayList<>();
}
