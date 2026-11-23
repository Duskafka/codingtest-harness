package com.duskafka.studybot.study;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.study.persistence.Invitation;
import com.duskafka.studybot.study.persistence.InvitationRepository;
import java.time.Instant;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사용 횟수 조건부 UPDATE (5.1 '정원 동시성', T20). 수락은 초대를 먼저 읽고 나중에 이 UPDATE를 한다. 그 사이에 철회·만료돼도
 * UPDATE가 최신 행으로 조건을 다시 봐서 가입하지 못해야 한다. 그래서 UPDATE 하나만 따로 본다.
 */
class InvitationUseCountIntegrationTest extends StudyTestSupport {

	private static final Instant NOW = Instant.parse("2026-11-20T06:00:00Z");

	@Autowired
	private InvitationRepository invitationRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private long studyId;

	private TestUser owner;

	@BeforeEach
	void setUp() {
		setClock(NOW.toString());
		owner = newUser("use-count-owner");
		studyId = createStudy(owner, "GROUP");
	}

	@Test
	@DisplayName("T20: 쓸 수 있는 초대면 사용 횟수가 하나 는다")
	void usableInvitationIsCounted() {
		long id = insertInvitation("2026-11-27T06:00:00Z", 2, 1, null);

		assertThat(increment(id)).isEqualTo(1);
		assertThat(usedCount(id)).isEqualTo(2);
	}

	@Test
	@DisplayName("T20: 철회된 초대는 사용 횟수가 남아 있어도 늘지 않는다 (읽은 뒤 철회가 커밋된 경우)")
	void revokedInvitationIsNotCounted() {
		long id = insertInvitation("2026-11-27T06:00:00Z", 30, 0, "2026-11-20T05:59:00Z");

		assertThat(increment(id)).isZero();
		assertThat(usedCount(id)).isZero();
	}

	@Test
	@DisplayName("T20: 만료 시각이 된 초대는 늘지 않는다")
	void expiredInvitationIsNotCounted() {
		long id = insertInvitation(NOW.toString(), 30, 0, null);

		assertThat(increment(id)).isZero();
	}

	@Test
	@DisplayName("T20: 사용 횟수를 다 쓴 초대는 늘지 않는다")
	void usedUpInvitationIsNotCounted() {
		long id = insertInvitation("2026-11-27T06:00:00Z", 1, 1, null);

		assertThat(increment(id)).isZero();
		assertThat(usedCount(id)).isEqualTo(1);
	}

	/**
	 * 철회({@code InvitationService.revoke})는 초대를 읽고 {@code revokedAt}만 바꾼다. 그 사이에 다른 수락이 사용 횟수를 올리고
	 * 커밋해도, 철회 커밋이 엔티티에 남은 옛 사용 횟수로 덮어쓰면 안 된다 (CodeRabbit PR #11 리뷰).
	 * 수락 쪽은 별도 트랜잭션(REQUIRES_NEW)으로 먼저 커밋해서 스레드 없이 같은 순서를 만든다.
	 */
	@Test
	@DisplayName("T20: 철회가 초대를 읽은 뒤 다른 수락이 사용 횟수를 올려도 철회 커밋이 그 값을 되돌리지 않는다")
	void revokeDoesNotOverwriteConcurrentUseCount() {
		long id = insertInvitation("2026-11-27T06:00:00Z", 30, 0, null);
		TransactionTemplate concurrentAccept = new TransactionTemplate(transactionManager);
		concurrentAccept.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		transactionTemplate.executeWithoutResult(status -> {
			Invitation invitation = invitationRepository.findByIdAndStudyId(id, studyId).orElseThrow();
			concurrentAccept.executeWithoutResult(inner -> invitationRepository.incrementUsedCount(id, NOW));
			invitation.revoke(NOW);
		});

		assertThat(usedCount(id)).isEqualTo(1);
		assertThat(jdbc.sql("select revoked_at is not null from studybot.invitations where id = :id")
			.param("id", id)
			.query(Boolean.class)
			.single()).isTrue();
	}

	private int increment(long invitationId) {
		Integer updated = transactionTemplate
			.execute(status -> invitationRepository.incrementUsedCount(invitationId, NOW));
		return updated != null ? updated : -1;
	}

	private long insertInvitation(String expiresAt, int maxUses, int usedCount, @Nullable String revokedAt) {
		byte[] hash = new byte[32];
		java.util.concurrent.ThreadLocalRandom.current().nextBytes(hash);
		return jdbc.sql("""
				insert into studybot.invitations
					(study_id, token_hash, expires_at, max_uses, used_count, revoked_at, created_by, created_at)
				values (:studyId, decode(:hash, 'hex'), cast(:expiresAt as timestamptz), :maxUses, :usedCount,
					cast(:revokedAt as timestamptz), :createdBy, cast(:createdAt as timestamptz))
				returning id
				""")
			.param("studyId", studyId)
			.param("hash", HexFormat.of().formatHex(hash))
			.param("expiresAt", expiresAt)
			.param("maxUses", maxUses)
			.param("usedCount", usedCount)
			.param("revokedAt", revokedAt, java.sql.Types.VARCHAR)
			.param("createdBy", owner.id())
			.param("createdAt", NOW.toString())
			.query(Long.class)
			.single();
	}

	private int usedCount(long invitationId) {
		return jdbc.sql("select used_count from studybot.invitations where id = :id")
			.param("id", invitationId)
			.query(Integer.class)
			.single();
	}

}
