package com.duskafka.studybot.study.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * 초대 (data-model.md 4.3 {@code invitations}, 5.1). 토큰 원문은 저장하지 않고 SHA-256 해시만 둔다.
 * 사용 횟수는 {@link InvitationRepository#incrementUsedCount}의 조건부 UPDATE로만 늘린다 (5.1 '정원 동시성').
 */
@Entity
@Table(name = "invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invitation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "study_id", nullable = false)
	private Long studyId;

	@Getter(AccessLevel.NONE)
	@Column(name = "token_hash", nullable = false)
	private byte[] tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "max_uses", nullable = false)
	private int maxUses;

	/**
	 * 사용 횟수. 엔티티를 저장할 때는 쓰지 않는다({@code updatable = false}). 철회처럼 다른 컬럼을 바꾸는 저장이 동시 수락이
	 * 올린 값을 옛 값으로 덮어쓰지 않게 하기 위해서다 (CodeRabbit PR #11 리뷰). 늘리는 건 조건부 UPDATE만 한다.
	 */
	@Column(name = "used_count", nullable = false, updatable = false)
	private int usedCount;

	@Column(name = "revoked_at")
	private @Nullable Instant revokedAt;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public Invitation(long studyId, byte[] tokenHash, Instant expiresAt, int maxUses, long createdBy,
			Instant createdAt) {
		if (maxUses < 1) {
			throw new IllegalArgumentException("사용 횟수는 1 이상이어야 해요");
		}
		this.studyId = studyId;
		this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash").clone();
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
		this.maxUses = maxUses;
		this.usedCount = 0;
		this.createdBy = createdBy;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}

	/** 철회한다. 이미 철회했으면 처음 철회한 시각을 그대로 둔다. */
	public void revoke(Instant at) {
		if (this.revokedAt == null) {
			this.revokedAt = Objects.requireNonNull(at, "at");
		}
	}

	public boolean isRevoked() {
		return this.revokedAt != null;
	}

	/** 만료 시각이 되면(같은 시각 포함) 쓸 수 없다. */
	public boolean isExpiredAt(Instant now) {
		return !now.isBefore(this.expiresAt);
	}

	public boolean isUsedUp() {
		return this.usedCount >= this.maxUses;
	}

}
