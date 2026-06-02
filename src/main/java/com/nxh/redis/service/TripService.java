package com.nxh.redis.service;
import com.nxh.redis.dto.page.PageResponseDto;
import com.nxh.redis.dto.trip.TripRequest;
import com.nxh.redis.dto.trip.TripResponse;

import java.util.List;

public interface TripService {
    PageResponseDto<TripResponse> getTrips(int page, int size, List<String> sorts);
    TripResponse createTrip(TripRequest request);
}
