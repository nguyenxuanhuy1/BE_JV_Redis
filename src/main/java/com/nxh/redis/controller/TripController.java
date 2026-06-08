package com.nxh.redis.controller;

import com.nxh.redis.dto.ApiResponse;
import com.nxh.redis.dto.page.PageResponseDto;
import com.nxh.redis.dto.trip.TripRequest;
import com.nxh.redis.dto.trip.TripResponse;
import com.nxh.redis.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {
    private final TripService tripService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponseDto<TripResponse>>> getTrips(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt:desc") List<String> sorts) {

        PageResponseDto<TripResponse> data = tripService.getTrips(page, size, sorts);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TripResponse>> createTrip(@RequestBody TripRequest request) {
        TripResponse data = tripService.createTrip(request);
        return ResponseEntity.ok(ApiResponse.success("Tạo chuyến đi thành công", data));
    }
}

