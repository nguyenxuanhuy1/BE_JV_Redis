package com.nxh.redis.controller;
import com.nxh.redis.dto.page.PageResponseDto;
import com.nxh.redis.dto.trip.TripRequest;
import com.nxh.redis.dto.trip.TripResponse;
import com.nxh.redis.service.TripService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {
    private final TripService tripService;
//    private final TripStreamService tripStreamService;

    @GetMapping
    public PageResponseDto<TripResponse> getTrips(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt:desc") List<String> sorts) {

        return tripService.getTrips(page, size, sorts);
    }

    @PostMapping
    public TripResponse createTrip(@RequestBody TripRequest request) {
        return tripService.createTrip(request);
    }

//    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<TripResponse> stream() {
//        return tripStreamService.getStream();
//    }
}
