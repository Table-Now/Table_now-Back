package zerobase.tableNow.domain.store.entity;

import jakarta.persistence.*;
import lombok.*;
import org.joda.time.LocalDateTime;
import zerobase.tableNow.domain.baseEntity.BaseEntity;
import zerobase.tableNow.domain.reservation.entity.ReservationEntity;
import zerobase.tableNow.domain.user.entity.UsersEntity;

import java.util.ArrayList;
import java.util.List;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "store")
public class StoreEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user")
    private UsersEntity user;

    private String store;
    private String storeLocation;
    @Column(name = "storeImg")
    private String storeImg;
    private String storeContents;

    @Column(nullable = true)
    @Builder.Default
    private Integer rating = 0; // 별점
    private String storeOpen;
    private String storeClose;
    private String storeWeekOff;

    private double latitude;  // 위도 추가
    private double longitude; // 경도 추가

    @Transient
    private Double distance; // 거리순

    @OneToMany(mappedBy = "store", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @Builder.Default
    private List<ReservationEntity> reservations = new ArrayList<>();

    private Boolean isQueueRestricted;  // 줄서기 금지 상태
    private LocalDateTime queueRestrictionEndTime;  // 줄서기 금지 종료 시간
}
