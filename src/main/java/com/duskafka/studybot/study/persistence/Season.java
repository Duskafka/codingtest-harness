package com.duskafka.studybot.study.persistence;

import com.duskafka.studybot.study.SeasonStatus;
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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 기수 (data-model.md 4.3 {@code seasons}, R13). 시작일부터 끝 날짜까지(양 끝 포함) 운영한다. */
@Entity
@Table(name = "seasons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Season {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "study_id", nullable = false)
	private Long studyId;

	@Column(name = "seq", nullable = false)
	private int seq;

	@Column(name = "start_day", nullable = false)
	private LocalDate startDay;

	/** 기수의 마지막 스터디 일 (포함). */
	@Column(name = "end_day", nullable = false)
	private LocalDate endDay;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private SeasonStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/**
	 * 새 기수. 상태는 {@code SCHEDULED}다 (3.4 '[*] → SCHEDULED: 스터디 생성 또는 다음 기수 생성').
	 * 끝 날짜는 시작일부터 기수 길이만큼 센 마지막 날이다 (R13). 28일이면 시작일 + 27일.
	 */
	public Season(long studyId, int seq, LocalDate startDay, int lengthDays, Instant createdAt) {
		if (seq < 1 || lengthDays < 1) {
			throw new IllegalArgumentException("기수 번호와 길이는 1 이상이어야 해요");
		}
		this.studyId = studyId;
		this.seq = seq;
		this.startDay = Objects.requireNonNull(startDay, "startDay");
		this.endDay = startDay.plusDays(lengthDays - 1L);
		this.status = SeasonStatus.SCHEDULED;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}

	public boolean contains(LocalDate studyDay) {
		return !studyDay.isBefore(this.startDay) && !studyDay.isAfter(this.endDay);
	}

}
