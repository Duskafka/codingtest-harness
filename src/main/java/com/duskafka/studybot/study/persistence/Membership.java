package com.duskafka.studybot.study.persistence;

import com.duskafka.studybot.study.MembershipRole;
import com.duskafka.studybot.study.StudyMember;
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
import org.jspecify.annotations.Nullable;

/**
 * 멤버십 (data-model.md 4.3 {@code memberships}, R14). 탈퇴하면 {@code leftAt}을 채우고 행은 남긴다.
 * 다시 가입하면 새 행을 만든다 (탈퇴하지 않은 행에만 걸리는 부분 UNIQUE).
 */
@Entity
@Table(name = "memberships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Membership {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "study_id", nullable = false)
	private Long studyId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false)
	private MembershipRole role;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	/** 판정을 시작하는 스터디 일 (R14: 가입 다음 스터디 일). */
	@Column(name = "judged_from", nullable = false)
	private LocalDate judgedFrom;

	/** 탈퇴한 시각. 그 시각이 속한 스터디 일까지 판정한다 (R14, 5.16). */
	@Column(name = "left_at")
	private @Nullable Instant leftAt;

	public Membership(long studyId, long userId, MembershipRole role, Instant joinedAt, LocalDate judgedFrom) {
		this.studyId = studyId;
		this.userId = userId;
		this.role = Objects.requireNonNull(role, "role");
		this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt");
		this.judgedFrom = Objects.requireNonNull(judgedFrom, "judgedFrom");
	}

	public boolean isOwner() {
		return this.role == MembershipRole.OWNER;
	}

	/** 탈퇴한다. 방장은 먼저 위임해야 한다 (R14). 그 확인은 부르는 쪽이 한다. */
	public void leave(Instant at) {
		if (this.leftAt != null) {
			throw new IllegalStateException("이미 탈퇴한 멤버십이에요");
		}
		this.leftAt = Objects.requireNonNull(at, "at");
	}

	public void promoteToOwner() {
		this.role = MembershipRole.OWNER;
	}

	public void demoteToMember() {
		this.role = MembershipRole.MEMBER;
	}

	public StudyMember toStudyMember() {
		return new StudyMember(this.studyId, this.userId, this.role, this.judgedFrom);
	}

}
