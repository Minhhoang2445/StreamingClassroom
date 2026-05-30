package com.zeet.StreamingClassRoom.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zeet.StreamingClassRoom.DTO.CreateLiveSessionRequest;
import com.zeet.StreamingClassRoom.DTO.LiveSessionResponse;
import com.zeet.StreamingClassRoom.DTO.LiveSessionTokenResponse;
import com.zeet.StreamingClassRoom.DTO.UpdateLiveSessionRequest;
import com.zeet.StreamingClassRoom.exception.BadRequestException;
import com.zeet.StreamingClassRoom.exception.ForbiddenException;
import com.zeet.StreamingClassRoom.exception.ResourceNotFoundException;
import com.zeet.StreamingClassRoom.model.Classroom;
import com.zeet.StreamingClassRoom.model.ClassroomMemberRole;
import com.zeet.StreamingClassRoom.model.ClassroomMemberStatus;
import com.zeet.StreamingClassRoom.model.LiveSession;
import com.zeet.StreamingClassRoom.model.LiveSessionStatus;
import com.zeet.StreamingClassRoom.model.Role;
import com.zeet.StreamingClassRoom.model.User;
import com.zeet.StreamingClassRoom.repository.ClassroomMemberRepository;
import com.zeet.StreamingClassRoom.repository.ClassroomRepository;
import com.zeet.StreamingClassRoom.repository.LiveSessionRepository;
import com.zeet.StreamingClassRoom.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class LiveSessionService {
    @Value("${livekit.url}")
    private String livekitUrl;
    private static final int DEFAULT_EMPTY_TIMEOUT_SECONDS = 300;
    private static final int DEFAULT_DEPARTURE_TIMEOUT_SECONDS = 20;
    private static final int DEFAULT_MAX_PARTICIPANTS = 0;

    private final LiveSessionRepository liveSessionRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomMemberRepository classroomMemberRepository;
    private final UserRepository userRepository;
    private final StreamService streamService;
    public LiveSessionResponse createSession(
            String classroomId,
            CreateLiveSessionRequest request,
            Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        Classroom classroom = getClassroomOrThrow(classroomId);
        ensureCanManageClassroom(classroom, currentUser);
        validateSchedule(request.scheduledStartTime(), request.scheduledEndTime());

        LiveSession session = new LiveSession();
        session.setClassroom(classroom);
        session.setHost(resolveHost(currentUser, classroom));
        session.setTitle(request.title());
        session.setDescription(request.description());
        session.setStatus(LiveSessionStatus.SCHEDULED);
        session.setScheduledStartTime(request.scheduledStartTime());
        session.setScheduledEndTime(request.scheduledEndTime());
        session.setEmptyTimeoutSeconds(resolveOrDefault(
                request.emptyTimeoutSeconds(),
                DEFAULT_EMPTY_TIMEOUT_SECONDS,
                "emptyTimeoutSeconds"));
        session.setDepartureTimeoutSeconds(resolveOrDefault(
                request.departureTimeoutSeconds(),
                DEFAULT_DEPARTURE_TIMEOUT_SECONDS,
                "departureTimeoutSeconds"));
        session.setMaxParticipants(resolveOrDefault(
                request.maxParticipants(),
                DEFAULT_MAX_PARTICIPANTS,
                "maxParticipants"));

        return mapToResponse(liveSessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public List<LiveSessionResponse> getSessionsByClassroom(String classroomId, Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        Classroom classroom = getClassroomOrThrow(classroomId);
        ensureCanViewClassroomSessions(classroom, currentUser);

        return liveSessionRepository.findByClassroomIdOrderByScheduledStartTimeDesc(classroom.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LiveSessionResponse getSessionById(String sessionId, Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        LiveSession session = getSessionOrThrow(sessionId);
        ensureCanViewClassroomSessions(session.getClassroom(), currentUser);

        return mapToResponse(session);
    }

    public LiveSessionResponse updateSession(
            String sessionId,
            UpdateLiveSessionRequest request,
            Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        LiveSession session = getSessionOrThrow(sessionId);
        ensureCanManageClassroom(session.getClassroom(), currentUser);
        ensureSessionCanBeUpdated(session, request);

        LocalDateTime scheduledStartTime = request.scheduledStartTime() != null
                ? request.scheduledStartTime()
                : session.getScheduledStartTime();
        LocalDateTime scheduledEndTime = request.scheduledEndTime() != null
                ? request.scheduledEndTime()
                : session.getScheduledEndTime();
        validateSchedule(scheduledStartTime, scheduledEndTime);

        if (hasText(request.title())) {
            session.setTitle(request.title());
        }

        if (request.description() != null) {
            session.setDescription(request.description());
        }

        if (request.status() != null) {
            session.setStatus(request.status());
        }

        if (request.scheduledStartTime() != null) {
            session.setScheduledStartTime(request.scheduledStartTime());
        }

        if (request.scheduledEndTime() != null) {
            session.setScheduledEndTime(request.scheduledEndTime());
        }

        if (request.emptyTimeoutSeconds() != null) {
            session.setEmptyTimeoutSeconds(validateNonNegative(
                    request.emptyTimeoutSeconds(),
                    "emptyTimeoutSeconds"));
        }

        if (request.departureTimeoutSeconds() != null) {
            session.setDepartureTimeoutSeconds(validateNonNegative(
                    request.departureTimeoutSeconds(),
                    "departureTimeoutSeconds"));
        }

        if (request.maxParticipants() != null) {
            session.setMaxParticipants(validateNonNegative(
                    request.maxParticipants(),
                    "maxParticipants"));
        }

        return mapToResponse(liveSessionRepository.save(session));
    }

    public void deleteSession(String sessionId, Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        LiveSession session = getSessionOrThrow(sessionId);
        ensureCanManageClassroom(session.getClassroom(), currentUser);

        if (session.getStatus() == LiveSessionStatus.LIVE) {
            throw new BadRequestException("Cannot delete a live session while it is LIVE");
        }

        liveSessionRepository.delete(session);
    }
    public LiveSessionTokenResponse startSession(String sessionId, Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        LiveSession session = getSessionOrThrow(sessionId);
        ensureCanManageClassroom(session.getClassroom(), currentUser);

        if (session.getStatus() == LiveSessionStatus.LIVE) {
            throw new BadRequestException("Session is already LIVE");
        }

        String roomName = session.getLivekitRoomName();
        if (roomName == null || roomName.isBlank()) {
        roomName = generateRoomName(session);
        session.setLivekitRoomName(roomName);
        }

        if (session.getStatus() != LiveSessionStatus.LIVE) {
            session.setStatus(LiveSessionStatus.LIVE);
        }

        if (session.getActualStartTime() == null) {
            session.setActualStartTime(LocalDateTime.now());
        }

        LiveSession savedSession = liveSessionRepository.save(session);

        String token = streamService.generateToken(
            savedSession.getLivekitRoomName(),
            currentUser.getId(),
            currentUser.getUsername(),
            true
    );

    return new LiveSessionTokenResponse(
            savedSession.getId(),
            savedSession.getClassroom().getId(),
            savedSession.getLivekitRoomName(),
            livekitUrl,
            token,
            currentUser.getId(),
            currentUser.getUsername(),
            savedSession.getStatus()
    );
}
    public LiveSessionTokenResponse joinSession(String sessionId, Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        LiveSession session = getSessionOrThrow(sessionId);

        if (session.getStatus() != LiveSessionStatus.LIVE) {
            throw new BadRequestException("Cannot join a session that is not LIVE");
        }

        if (!isActiveMember(session.getClassroom().getId(), currentUser.getId())) {
            throw new ForbiddenException("You are not an active member of this classroom");
        }

        String token = streamService.generateToken(
            session.getLivekitRoomName(),
            currentUser.getId(),
            currentUser.getUsername(),
            false
    );
        return new LiveSessionTokenResponse(
            session.getId(),
            session.getClassroom().getId(),
            session.getLivekitRoomName(),
            livekitUrl,
            token,
            currentUser.getId(),
            currentUser.getUsername(),
            session.getStatus()
    );
    }
    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ForbiddenException("Authentication is required");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
    }

    private Classroom getClassroomOrThrow(String classroomId) {
        return classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));
    }

    private LiveSession getSessionOrThrow(String sessionId) {
        return liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Live session not found"));
    }

    private void ensureCanViewClassroomSessions(Classroom classroom, User user) {
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.TEACHER) {
            return;
        }

        if (user.getRole() == Role.STUDENT && isActiveMember(classroom.getId(), user.getId())) {
            return;
        }

        throw new ForbiddenException("You do not have access to this classroom's sessions");
    }

    private void ensureCanManageClassroom(Classroom classroom, User user) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }

        if (user.getRole() == Role.TEACHER && canTeacherManageClassroom(classroom, user)) {
            return;
        }

        throw new ForbiddenException("You do not have permission to manage sessions in this classroom");
    }

    private boolean canTeacherManageClassroom(Classroom classroom, User user) {
        if (classroom.getTeacher().getId().equals(user.getId())) {
            return true;
        }

        return classroomMemberRepository.existsByClassroomIdAndUserIdAndStatusAndRole(
                classroom.getId(),
                user.getId(),
                ClassroomMemberStatus.ACTIVE,
                ClassroomMemberRole.TEACHER);
    }

    private boolean isActiveMember(String classroomId, String userId) {
        return classroomMemberRepository.existsByClassroomIdAndUserIdAndStatus(
                classroomId,
                userId,
                ClassroomMemberStatus.ACTIVE);
    }

    private User resolveHost(User currentUser, Classroom classroom) {
        if (currentUser.getRole() == Role.ADMIN) {
            return classroom.getTeacher();
        }

        return currentUser;
    }

    private void validateSchedule(LocalDateTime scheduledStartTime, LocalDateTime scheduledEndTime) {
        if (scheduledStartTime != null
                && scheduledEndTime != null
                && !scheduledEndTime.isAfter(scheduledStartTime)) {
            throw new BadRequestException("scheduledEndTime must be after scheduledStartTime");
        }
    }

    private void ensureSessionCanBeUpdated(LiveSession session, UpdateLiveSessionRequest request) {
        if ((session.getStatus() == LiveSessionStatus.LIVE || session.getStatus() == LiveSessionStatus.ENDED)
                && hasRestrictedUpdate(request)) {
            throw new BadRequestException("Cannot update title, schedule, status, or room settings for LIVE or ENDED sessions");
        }
    }

    private boolean hasRestrictedUpdate(UpdateLiveSessionRequest request) {
        return request.title() != null
                || request.status() != null
                || request.scheduledStartTime() != null
                || request.scheduledEndTime() != null
                || request.emptyTimeoutSeconds() != null
                || request.departureTimeoutSeconds() != null
                || request.maxParticipants() != null;
    }

    private Integer resolveOrDefault(Integer value, Integer defaultValue, String fieldName) {
        if (value == null) {
            return defaultValue;
        }

        return validateNonNegative(value, fieldName);
    }

    private Integer validateNonNegative(Integer value, String fieldName) {
        if (value < 0) {
            throw new BadRequestException(fieldName + " must be greater than or equal to 0");
        }

        return value;
    }

    private LiveSessionResponse mapToResponse(LiveSession session) {
        return new LiveSessionResponse(
                session.getId(),
                session.getClassroom().getId(),
                session.getClassroom().getName(),
                session.getHost().getId(),
                session.getHost().getUsername(),
                session.getTitle(),
                session.getDescription(),
                session.getStatus(),
                session.getScheduledStartTime(),
                session.getScheduledEndTime(),
                session.getActualStartTime(),
                session.getActualEndTime(),
                session.getLivekitRoomName(),
                session.getLivekitRoomSid(),
                session.getEmptyTimeoutSeconds(),
                session.getDepartureTimeoutSeconds(),
                session.getMaxParticipants(),
                session.getRoomMetadata(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
    public String generateRoomName(LiveSession session) {
        return "classroom-" + session.getClassroom().getId() + "-session-" + session.getId();
    }
    public void endSessionByLivekitRoomName(String livekitRoomName) {
        
        liveSessionRepository.findByLivekitRoomName(livekitRoomName).ifPresent(session -> {
            if (session.getStatus() == LiveSessionStatus.LIVE) {
                session.setStatus(LiveSessionStatus.ENDED);
                session.setActualEndTime(LocalDateTime.now());
                liveSessionRepository.save(session);
            }
        });
    }
}
