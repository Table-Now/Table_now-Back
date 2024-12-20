package zerobase.tableNow.domain.store.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import zerobase.tableNow.components.S3UploadComponents;
import zerobase.tableNow.domain.constant.SortType;
import zerobase.tableNow.domain.store.dto.StoreDto;
import zerobase.tableNow.domain.store.entity.StoreEntity;
import zerobase.tableNow.domain.store.mapper.StoreMapper;
import zerobase.tableNow.domain.store.repository.StoreRepository;
import zerobase.tableNow.domain.store.service.LocationService;
import zerobase.tableNow.domain.store.service.StoreService;
import zerobase.tableNow.domain.user.entity.UsersEntity;
import zerobase.tableNow.domain.user.repository.UserRepository;
import zerobase.tableNow.exception.TableException;
import zerobase.tableNow.exception.type.ErrorCode;

import java.io.IOException;
import java.time.*;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class StoreServiceImpl implements StoreService {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final StoreMapper storeMapper;
    private final LocationService locationService;
    private final S3UploadComponents s3UploadComponents;

    /**
     * 상점등록
     * @param storeDto
     * @return 상점 등록 내용
     */
    @Override
    @Transactional
    @CacheEvict(value = "stores", allEntries = true)
    public StoreDto register(StoreDto storeDto, MultipartFile image) {
        UsersEntity users = userRepository.findByUser(storeDto.getUser())
                .orElseThrow(()-> new TableException((ErrorCode.USER_NOT_FOUND)));

        if (users.getPhone().isEmpty()){
            throw new TableException(ErrorCode.INTERNAL_SERVER_ERROR,"전화번호를 등록해주세요.");
        }

        Optional<StoreEntity> optionalStoreEntity = storeRepository.findByStore(storeDto.getStore());

        if (optionalStoreEntity.isPresent()){
            log.info("해당 상점이 존재합니다.");
            throw new RuntimeException("해당 상점이 존재합니다.");
        }
        // 주소를 위도, 경도로 변환
        double[] coordinates = locationService.getCoordinates(storeDto.getStoreLocation());
        storeDto.setLatitude(coordinates[0]);
        storeDto.setLongitude(coordinates[1]);

        String imageUrl = null;
        if (image != null && !image.isEmpty()) {
            try {
                imageUrl = s3UploadComponents.upload(image);
            } catch (IOException e) {
                throw new RuntimeException("이미지 업로드에 실패했습니다.", e);
            }
            storeDto.setStoreImg(imageUrl);
        }

        // DTO -> Entity 변환 및 저장
        StoreEntity storeEntity = storeMapper.toStoreEntity(storeDto,users);
        StoreEntity saveEntity = storeRepository.save(storeEntity);

        return storeMapper.toStoreDto(saveEntity);
    }

    /**
     * 상점 목록
     * @param keyword
     * @param sortType
     * @param userLat
     * @param userLon
     * @return 필터를 통한 상점 목록 반환
     */
    @Cacheable(
            value = "stores",
            key = "'list_' " +
                    "+ (#keyword != null ? #keyword : 'all') + '_' " +
                    "+ (#sortType != null ? #sortType : 'default') + '_' " +
                    "+ (#userLat != null ? #userLat : 0) + '_' " +
                    "+ (#userLon != null ? #userLon : 0)"
    )
    public List<StoreDto> getAllStores(
            String keyword,
            SortType sortType,
            Double userLat,
            Double userLon
    ) {
        List<StoreEntity> storeEntities;

        // 기본 데이터 조회
        if (keyword != null && !keyword.trim().isEmpty()) {
            storeEntities = storeRepository.findByStoreContainingIgnoreCase(keyword.trim());
        } else {
            storeEntities = storeRepository.findAll();
        }

        //요일 필터링
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        String todayInKorean = convertDayOfWeekToKorean(today);

        // 휴무일 필터링
        storeEntities = storeEntities.stream()
                .filter(store -> store.getStoreWeekOff() == null || !store.getStoreWeekOff().contains(todayInKorean))
                .collect(Collectors.toList());

        // 거리 계산 및 정렬
        if (SortType.DISTANCE.equals(sortType) && userLat != null && userLon != null) {
            storeEntities.forEach(store -> {
                double distance = calculateDistance(
                        userLat, userLon,
                        store.getLatitude(), store.getLongitude()
                );
                store.setDistance(distance);
            });

            // 거리순으로 정렬
            storeEntities.sort(Comparator.comparingDouble(StoreEntity::getDistance));
        } else {
            // 다른 정렬 조건 적용
            if (sortType != null) {
                switch (sortType) {
                    case RATING_HIGH:
                        storeEntities.sort((a, b) -> compareRatings(b.getRating(), a.getRating()));
                        break;
                    case RATING_LOW:
                        storeEntities.sort((a, b) -> compareRatings(a.getRating(), b.getRating()));
                        break;
                }
            }
        }

        // DTO 변환 시 거리 정보도 포함
        return storeEntities.stream()
                .map(entity -> {
                    StoreDto dto = storeMapper.convertToDto(entity);
                    dto.setDistance(entity.getDistance());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    // 요일 메서드 추가
    private String convertDayOfWeekToKorean(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY:
                return "월요일";
            case TUESDAY:
                return "화요일";
            case WEDNESDAY:
                return "수요일";
            case THURSDAY:
                return "목요일";
            case FRIDAY:
                return "금요일";
            case SATURDAY:
                return "토요일";
            case SUNDAY:
                return "일요일";
            default:
                return "";
        }
    }

    // 거리 계산 메서드 추가
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // 지구의 반경 (km)

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c; // 거리 (km)
    }

    // Null 처리를 위한 헬퍼 메소드들
    private int compareRatings(Integer rating1, Integer rating2) {
        // null을 0으로 처리
        int r1 = rating1 == null ? 0 : rating1;
        int r2 = rating2 == null ? 0 : rating2;
        return Integer.compare(r1, r2);
    }


    /**
     * 상점 수정
     * @param id
     * @param storeDto
     * @return 상점 수정내용
     */
    @Override
    @Transactional
    public StoreDto update(Long id, StoreDto storeDto, MultipartFile file) {
        StoreEntity storeUpdate = storeRepository.findById(id)
                .orElseThrow(() -> new TableException(ErrorCode.PRODUCT_NOT_FOUND));

        UsersEntity currentUser = storeUpdate.getUser();
        storeUpdate.setUser(currentUser); // 기존 사용자 정보 유지

        // 이미지 파일이 제공된 경우 S3에 업로드
        if (file != null && !file.isEmpty()) {
            try {
                // 기존 이미지 URL이 있다면 S3에서 삭제 로직 추가 가능
                String imageUrl = s3UploadComponents.upload(file);
                storeUpdate.setStoreImg(imageUrl);
            } catch (IOException e) {
                throw new RuntimeException("이미지 업로드 중 오류가 발생했습니다.", e);
            }
        }

        // 각 필드에 대해 빈값 또는 공백만 있는 경우 기존 값을 유지
        if (storeDto.getStore() != null && !storeDto.getStore().trim().isEmpty()) {
            storeUpdate.setStore(storeDto.getStore());
        }

        // 위치가 변경된 경우 위도, 경도 업데이트
        if (storeDto.getStoreLocation() != null && !storeDto.getStoreLocation().trim().isEmpty()) {
            // 기존 위치와 다른 경우에만 위도,경도 업데이트
            if (!storeDto.getStoreLocation().equals(storeUpdate.getStoreLocation())) {
                double[] coordinates = locationService.getCoordinates(storeDto.getStoreLocation());
                storeUpdate.setLatitude(coordinates[0]);
                storeUpdate.setLongitude(coordinates[1]);
                storeUpdate.setStoreLocation(storeDto.getStoreLocation());
            }
        }

        if (storeDto.getStoreContents() != null && !storeDto.getStoreContents().trim().isEmpty()) {
            storeUpdate.setStoreContents(storeDto.getStoreContents());
        }

        if (storeDto.getRating() != null) {
            storeUpdate.setRating(storeDto.getRating());
        }

        if (storeDto.getStoreOpen() != null && !storeDto.getStoreOpen().trim().isEmpty()) {
            storeUpdate.setStoreOpen(storeDto.getStoreOpen());
        }

        if (storeDto.getStoreClose() != null && !storeDto.getStoreClose().trim().isEmpty()) {
            storeUpdate.setStoreClose(storeDto.getStoreClose());
        }

        if (storeDto.getStoreWeekOff() != null && !storeDto.getStoreWeekOff().trim().isEmpty()) {
            storeUpdate.setStoreWeekOff(storeDto.getStoreWeekOff());
        }

        storeUpdate.setUpdateAt(LocalDateTime.now());

        // 더티체킹을 통해 변경된 내용을 자동으로 반영
        return storeMapper.convertToDto(storeUpdate);
    }




    /**
     * 상점 상세정보
     * @param id
     * @return 상세정보
     */
    @Override
    @Transactional
    public StoreDto detail(Long id) {
        StoreEntity storeDetail = storeRepository.findById(id)
                .orElseThrow(()-> new TableException(ErrorCode.PRODUCT_NOT_FOUND));

        return storeMapper.convertToDto(storeDetail);
    }

    /**
     * 상점 삭제
     * @param id
     */
    @Override
    @Transactional
    public void delete(Long id) {
        storeRepository.deleteById(id);
    }


}
