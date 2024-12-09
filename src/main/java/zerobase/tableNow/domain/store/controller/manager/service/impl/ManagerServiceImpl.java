package zerobase.tableNow.domain.store.controller.manager.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import zerobase.tableNow.domain.constant.Status;
import zerobase.tableNow.domain.reservation.entity.ReservationEntity;
import zerobase.tableNow.domain.reservation.repository.ReservationRepository;
import zerobase.tableNow.domain.store.controller.manager.dto.ConfirmDto;
import zerobase.tableNow.domain.store.controller.manager.dto.ManagerDto;
import zerobase.tableNow.domain.store.controller.manager.repository.ManagerRepository;
import zerobase.tableNow.domain.store.controller.manager.service.ManagerService;
import zerobase.tableNow.domain.store.entity.StoreEntity;
import zerobase.tableNow.domain.store.repository.StoreRepository;
import zerobase.tableNow.domain.user.entity.UsersEntity;
import zerobase.tableNow.domain.user.repository.UserRepository;
import zerobase.tableNow.exception.TableException;
import zerobase.tableNow.exception.type.ErrorCode;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManagerServiceImpl implements ManagerService {
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final ReservationRepository reservationRepository;

    // 대기번호 큐
    private final Queue<Integer> waitingNumberQueue = new LinkedList<>();

    /**
     * 매니저 전용 상점 목록
     * @param userId
     * @return 해당 본인 상점에 대한 목록 반환
     */
    @Override
    public List<ManagerDto> managerList(String user) {
        UsersEntity userId = userRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다."));

        List<StoreEntity> storeEntities = storeRepository.findByUser(userId);

        // 조회된 상점 목록을 ManagerDto 리스트로 변환
        return storeEntities.stream().map(store -> ManagerDto.builder()
                .id(store.getId())
                .user(userId.getUser())
                .store(store.getStore())
                .storeLocation(store.getStoreLocation())
                .storeImg(store.getStoreImg())
                .storeContents(store.getStoreContents())
                .rating(store.getRating())
                .storeOpen(store.getStoreOpen())
                .storeClose(store.getStoreClose())
                .storeWeekOff(store.getStoreWeekOff())
                .build()
        ).collect(Collectors.toList());
    }


        /**
         * 매니저 전용 예약 정보 확인
         * @param store
         * @return 본인 상점에 대한 예약정보
         */
//        @Override
//        public List<ConfirmDto> confirmList(String store) {
//             storeRepository.findByStore(store)
//                    .orElseThrow(() -> new TableException(ErrorCode.PRODUCT_NOT_FOUND));
//
//            List<ReservationEntity> reservationEntity = reservationRepository.findByStore_Store(store);
//
//            // 대기번호가 부여된 예약만 필터링하고, 대기번호를 기준으로 오름차순 정렬
//            List<ConfirmDto> confirmDtoList = reservationEntity.stream()
//                    .filter(reservation -> reservation.getWaitingNumber() != null) // 대기번호가 부여된 예약만 필터링
//                    .sorted(Comparator.comparingInt(ReservationEntity::getWaitingNumber)) // 대기번호 순으로 정렬
//                    .map(reservation -> ConfirmDto.builder()
//                            .store(reservation.getStore().getStore())
//                            .phone(reservation.getPhone())
//                            .peopleNb(reservation.getPeopleNb())
//                            .waitingNumber(reservation.getWaitingNumber())
//                            .build())
//                    .collect(Collectors.toList());
//
//            return confirmDtoList;
//        }

    /**
     * 매니저가 대기자 목록을 확인하는 로직
     * 대기번호 큐를 활용하여 대기중인 예약 목록을 확인
     */
    public List<ConfirmDto> getWaitingList(String store) {
        storeRepository.findByStore(store)
                .orElseThrow(() -> new TableException(ErrorCode.PRODUCT_NOT_FOUND));

        // 대기번호 큐에 있는 대기자 번호들을 확인
        List<Integer> waitingNumbers = new ArrayList<>(waitingNumberQueue);

        // 대기자 번호에 해당하는 예약을 찾기 위해 예약 목록을 조회
        List<ReservationEntity> reservationEntity = reservationRepository.findByStore_Store(store);

        // 대기자 번호를 기반으로 예약 정보를 필터링
        List<ConfirmDto> waitingList = reservationEntity.stream()
                .filter(reservation -> waitingNumbers.contains(reservation.getWaitingNumber()))
                .sorted(Comparator.comparingInt(ReservationEntity::getWaitingNumber)) // 대기번호 순으로 정렬
                .map(reservation -> ConfirmDto.builder()
                        .store(reservation.getStore().getStore())
                        .phone(reservation.getPhone())
                        .peopleNb(reservation.getPeopleNb())
                        .waitingNumber(reservation.getWaitingNumber())
                        .build())
                .collect(Collectors.toList());

        return waitingList;
    }
}
