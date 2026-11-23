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
	 * 끝 날짜는 {@code StudyCalendar.seasonEndDay}로 센다 (5.13 '경계일 계산은 StudyCalendar 한 곳에서').
	 */
	public Season(long studyId, int seq, LocalDate startDay, LocalDate endDay, Instant createdAt) {
		Objects.requireNonNull(startDay, "startDay");
		Objects.requireNonNull(endDay, "endDay");
		if (seq < 1 || endDay.isBefore(startDay)) {
			throw new IllegalArgumentException("기수 번호는 1 이상, 끝 날짜는 시작일 이후여야 해요");
		}
		this.studyId = studyId;
		this.seq = seq;
		this.startDay = startDay;
		this.endDay = endDay;
		this.status = SeasonStatus.SCHEDULED;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}

	public boolean contains(LocalDate studyDay) {
		return !studyDay.isBefore(this.startDay) && !studyDay.isAfter(this.endDay);
	}

}
