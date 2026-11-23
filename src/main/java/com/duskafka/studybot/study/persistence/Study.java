package com.duskafka.studybot.study.persistence;

import com.duskafka.studybot.study.StudyMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** 스터디 (data-model.md 4.3 {@code studies}). 시간대는 만들 때 정하고 바꾸지 않는다 (5.1). */
@Entity
@Table(name = "studies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Study {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "mode", nullable = false)
	private StudyMode mode;

	@Column(name = "owner_id", nullable = false)
	private Long ownerId;

	/** 스터디 시간대 ID (예: {@code Asia/Seoul}). {@link #zoneId()}로 읽는다. */
	@Getter(AccessLevel.NONE)
	@Column(name = "timezone", nullable = false)
	private String timezone;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private StudyStatus status;

	/** 판정 배치의 진행 지점 (S12, 5.10). */
	@Column(name = "last_judged_day")
	private @Nullable LocalDate lastJudgedDay;

	/** 알림 생성의 진행 지점 (S13, 5.8). */
	@Column(name = "last_reminded_day")
	private @Nullable LocalDate lastRemindedDay;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public Study(String name, StudyMode mode, long ownerId, ZoneId zoneId, Instant createdAt) {
		this.name = Objects.requireNonNull(name, "name");
		this.mode = Objects.requireNonNull(mode, "mode");
		this.ownerId = ownerId;
		this.timezone = Objects.requireNonNull(zoneId, "zoneId").getId();
		this.status = StudyStatus.ACTIVE;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}

	public ZoneId zoneId() {
		return ZoneId.of(this.timezone);
	}

	public void rename(String newName) {
		this.name = Objects.requireNonNull(newName, "newName");
	}

	/** 방장을 바꾼다. 멤버십의 역할도 함께 바꿔야 한다 ({@link Membership#promoteToOwner()}). */
	public void changeOwner(long newOwnerId) {
		this.ownerId = newOwnerId;
	}

}
