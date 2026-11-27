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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * 기수 (data-model.md 4.3 {@code seasons}, R13). 시작일부터 끝 날짜까지(양 끝 포함) 운영한다.
 *
 * <p>상태는 판정 배치가 바꾼다 (S12): 시작일이 되면 진행, 마지막 날 판정이 끝나면 정산 중({@code SeasonRepository#markSettling}), 그 뒤
 * 72시간이 지나면 종료. 상태는 배치가 돌 때 바뀌어 실제 날짜보다 늦을 수 있으니, 시작했는지·그날이 기수 안인지는 상태가 아니라 날짜로
 * 본다 (5.13 '주의할 것').
 */
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
	 * 정산 중이 된 시각 (S12, V10). 마지막 날 판정이 끝난 시각이고, 이 시각 + {@link #SETTLEMENT_PERIOD}가 지나면 종료가 된다.
	 * 정산 중·종료일 때만 있다 (CHECK).
	 */
	@Column(name = "settling_started_at")
	private @Nullable Instant settlingStartedAt;

	/**
	 * 이의 기간 (R11·R13 '3일'). 정산 중이 된 실제 시각부터 72시간이다 (사람이 정함, 2026-11-27 "실제 판정 시각 + 72시간").
	 * 판정이 늦어도 이의 기간이 줄지 않는다.
	 */
	public static final Duration SETTLEMENT_PERIOD = Duration.ofHours(72);

	/**
	 * 새 기수. 상태는 {@code SCHEDULED}다 (3.4 '[*] → SCHEDULED: 스터디 생성(1기) 또는 이전 기수 시작(다음 기수)').
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

	/**
	 * 시작일이 된 예정 기수를 진행으로 바꾼다 (3.4 'SCHEDULED → ACTIVE: 시작일 (판정 배치)', S12). 시작했는지는 날짜로 본다
	 * (5.13 '주의할 것'). 예정이 아니거나 아직 시작일 전이면 바꾸지 않는다.
	 *
	 * @param today 규칙 버전을 반영해 센 오늘(스터디 일)
	 * @return 바꿨는지
	 */
	public boolean activateIfStarted(LocalDate today) {
		if (this.status != SeasonStatus.SCHEDULED || today.isBefore(this.startDay)) {
			return false;
		}
		this.status = SeasonStatus.ACTIVE;
		return true;
	}

	/**
	 * 정산 중이 된 지 이의 기간({@link #SETTLEMENT_PERIOD})이 지났으면 종료로 바꾼다 (3.4 'SETTLING → CLOSED', R13, S12).
	 *
	 * @return 바꿨는지
	 */
	public boolean closeIfSettled(Instant now) {
		if (this.status != SeasonStatus.SETTLING || this.settlingStartedAt == null
				|| now.isBefore(this.settlingStartedAt.plus(SETTLEMENT_PERIOD))) {
			return false;
		}
		this.status = SeasonStatus.CLOSED;
		return true;
	}

}
