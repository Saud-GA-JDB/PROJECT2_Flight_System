package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.Airplane;
import com.ga.saudsFlightSystem.model.FAAAdmin;
import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.model.request.AirplaneRequest;
import com.ga.saudsFlightSystem.model.request.ReviewAirplaneRequest;
import com.ga.saudsFlightSystem.model.request.response.AirplaneRequestResponse;
import com.ga.saudsFlightSystem.repository.AirplaneRepository;
import com.ga.saudsFlightSystem.repository.AirplaneRequestRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class FAAAdminService {
    private UserService userService;
    private AirplaneRepository airplaneRepository;
    private AirplaneRequestRepository airplaneRequestRepository;

    public ResponseEntity<?> getPendingAirplaneRequests() {
        User user = UserService.getCurrentLoggedInUser();
        if (!UserService.isAllowedEndpoint("faaadmin", user.getRole()) || user.getFaaAdmin() == null) {
            throw new IllegalEndpoint("Only FAA admins can view airplane activation requests!");
        }
        List<AirplaneRequestResponse> responses = new ArrayList<>();
        for (AirplaneRequest request : airplaneRequestRepository.findByStatus(AirplaneRequest.ApprovalStatus.PENDING)) {
            Airplane airplane = request.getAirplane();
            responses.add(new AirplaneRequestResponse(
                    request.getId(), airplane.getId(), airplane.getRegistrationNumber(), airplane.getModel(),
                    request.getStatus(), request.getReviewReason(), request.getRequestedAt(), request.getReviewedAt()));
        }
        return ResponseEntity.status(HttpStatus.OK).body(responses);
    }

    public ResponseEntity<?> reviewAirplaneRequest(Long requestId, ReviewAirplaneRequest review) {
        User user = UserService.getCurrentLoggedInUser();
        if (!UserService.isAllowedEndpoint("faaadmin", user.getRole()) || user.getFaaAdmin() == null) {
            throw new IllegalEndpoint("Only FAA admins can review airplane activation requests!");
        }
        if (review.getStatus() != AirplaneRequest.ApprovalStatus.ACCEPTED &&
                review.getStatus() != AirplaneRequest.ApprovalStatus.DENIED) {
            throw new InvalidInformationException("Status must be ACCEPTED or DENIED");
        }
        String reason = review.getReviewReason();
        if (reason == null || reason.isBlank()) {
            if (review.getStatus() == AirplaneRequest.ApprovalStatus.DENIED) {
                throw new InvalidInformationException("A reason is required when denying an activation request");
            }
            reason = "approve";
        }
        if (reason.length() > 500) {
            throw new InvalidInformationException("Review reason must not exceed 500 characters");
        }
        AirplaneRequest request = airplaneRequestRepository.findById(requestId).orElseThrow(() -> new InformationNotFoundException("couldn't find airplane request with this id"));
        if (request.getStatus() != AirplaneRequest.ApprovalStatus.PENDING) {
            throw new InvalidInformationException("This activation request has already been reviewed");
        }
        Airplane airplane = request.getAirplane();
        if (review.getStatus() == AirplaneRequest.ApprovalStatus.ACCEPTED) {
            airplane.setStatus(Airplane.Status.ACTIVE);
        } else {
            airplane.setStatus(Airplane.Status.GROUNDED);
        }
        request.setStatus(review.getStatus());
        request.setReviewReason(reason);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewedBy(user.getFaaAdmin());
        airplaneRepository.save(airplane);
        airplaneRequestRepository.save(request);

        return ResponseEntity.status(HttpStatus.OK).body(new AirplaneRequestResponse(
                request.getId(), airplane.getId(), airplane.getRegistrationNumber(), airplane.getModel(),
                request.getStatus(), request.getReviewReason(), request.getRequestedAt(), request.getReviewedAt()));
    }

    public FAAAdmin findFAAAdminByEmailAddress(String email) {
        User user = userService.findUserByEmailAddress(email);
        if (user == null) {
            return null;
        }
        return user.getFaaAdmin();
    }
}
