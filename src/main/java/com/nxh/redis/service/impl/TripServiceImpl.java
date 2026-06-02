package com.nxh.redis.service.impl;
import com.nxh.redis.dto.page.PageResponseDto;
import com.nxh.redis.dto.trip.TripRequest;
import com.nxh.redis.dto.trip.TripResponse;
import com.nxh.redis.service.TripService;

import com.nxh.redis.entity.Trip;
import com.nxh.redis.repository.TripRepository;

import com.nxh.redis.util.PagingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripServiceImpl implements TripService {

    private final TripRepository tripRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public PageResponseDto<TripResponse> getTrips(int page, int size, List<String> sorts) {
        try {
            Pageable pageable = PagingUtil.buildPageable(page, size, sorts);

            Page<TripResponse> result = tripRepository.findAll(pageable)
                    .map(this::toResponse);

            return PageResponseDto.<TripResponse>builder()
                    .content(result.getContent())
                    .page(result.getNumber())
                    .size(result.getSize())
                    .totalElements(result.getTotalElements())
                    .totalPages(result.getTotalPages())
                    .isLast(result.isLast())
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Có lỗi xảy ra vui lòng thử lại sau", e);
        }
    }

    @Override
    public TripResponse createTrip(TripRequest request) {
        Trip trip = Trip.builder()
                .title(request.getTitle())
                .origin(request.getOrigin())
                .destination(request.getDestination())
                .price(request.getPrice())
                .build();

        tripRepository.save(trip);

        try {
            if (redisTemplate != null) {
                redisTemplate.delete("trip:list");
                redisTemplate.convertAndSend("trip:new", toResponse(trip));
            }
        } catch (Exception e) {
            log.error("Lỗi khi xóa cache/publish event Redis (Fail-Safe): {}", e.getMessage());
        }

        return toResponse(trip);
    }

    private TripResponse toResponse(Trip trip) {
        return TripResponse.builder()
                .id(trip.getId())
                .title(trip.getTitle())
                .origin(trip.getOrigin())
                .destination(trip.getDestination())
                .price(trip.getPrice())
                .createdAt(trip.getCreatedAt())
                .build();
    }
}
