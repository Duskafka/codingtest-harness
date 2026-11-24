package com.duskafka.studybot.study.service;

import com.duskafka.studybot.common.error.ConflictException;
import com.duskafka.studybot.common.error.GoneException;
import com.duskafka.studybot.common.error.NotFoundException;
import com.duskafka.studybot.study.MembershipRole;
import com.duskafka.studybot.study.StudyAccess;
import com.duskafka.studybot.study.StudyMode;
import com.duskafka.studybot.study.persistence.Invitation;
import com.duskafka.studybot.study.persistence.InvitationRepository;
import com.duskafka.studybot.study.persistence.Membership;
import com.duskafka.studybot.study.persistence.MembershipRepository;
import com.duskafka.studybot.study.persistence.Season;
import com.duskafka.studybot.study.persistence.SeasonRepository;
import com.duskafka.studybot.study.persistence.Study;
import com.duskafka.studybot.study.persistence.StudyRepository;
import com.duskafka.studybot.study.persistence.StudyRule;
import com.duskafka.studybot.study.persistence.StudyRuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초대 만들기·철회·미리보기·수락 (S05 만들 것 5·6·8, 5.1).
 *
 * <p>없는 토큰은 404, 있지만 쓸 수 없는 토큰(만료·철회·사용 횟수 소진)은 410이다 (S05 PR 판단 기록).
 * 철회 엔드포인트는 api.md 4.4에 없지만 사람이 만들기로 정했다 (2026-11-23, S05 세션 "DELETE 엔드포인트 추가").
 */
@Service
@RequiredArgsConstructor
public class InvitationService {

	static final String NOT_FOUND_DETAIL = "초대를 찾을 수 없어요.";

	private final StudyAccess studyAccess;

	private final StudyRepository studyRepository;

	private final StudyRuleRepository ruleRepository;

	private final SeasonRepository seasonRepository;

	private final MembershipRepository membershipRepository;

	private final InvitationRepository invitationRepository;

	private final InvitationTokens tokens;

	private final Clock clock;

	/**
	 * 초대를 만든다 (권한: 방장). 혼자 쓰기 스터디는 초대할 수 없다 (R15, 409). 토큰 원문은 돌려주는 값에만 있다.
	 * @param maxUses 사용 횟수 (1~{@value StudyLimits#MAX_INVITATION_USES})
	 */
	@Transactional
	public CreatedInvitation create(long studyId, long actorUserId, int maxUses) {
		this.studyAccess.requireOwner(studyId, actorUserId);
		Study study = this.studyRepository.findById(studyId)
			.orElseThrow(() -> new NotFoundException("스터디를 찾을 수 없어요."));
		if (study.getMode() == StudyMode.SOLO) {
			throw new ConflictException("혼자 쓰기 스터디에서는 초대할 수 없어요.");
		}
		String token = this.tokens.newToken();
		Instant now = this.clock.instant();
		Invitation invitation = this.invitationRepository.save(new Invitation(studyId, this.tokens.hash(token),
				now.plus(StudyLimits.INVITATION_TTL), maxUses, actorUserId, now));
		return new CreatedInvitation(invitation.getId(), token, invitation.getExpiresAt(), invitation.getMaxUses());
	}

	/** 초대를 철회한다 (권한: 방장). 다른 스터디의 초대 ID면 404. 이미 철회했으면 그대로 둔다. */
	@Transactional
	public void revoke(long studyId, long actorUserId, long invitationId) {
		this.studyAccess.requireOwner(studyId, actorUserId);
		Invitation invitation = this.invitationRepository.findByIdAndStudyId(invitationId, studyId)
			.orElseThrow(() -> new NotFoundException(NOT_FOUND_DETAIL));
		invitation.revoke(this.clock.instant());
	}

	/** 초대 미리보기 (권한: 공개). 가입 전에 규칙 요약을 보여 준다 (5.1 '주의할 것'). */
	@Transactional(readOnly = true)
	public InvitationPreview preview(String token) {
		Invitation invitation = find(token);
		Instant now = this.clock.instant();
		requireUsable(invitation, now);
		if (invitation.isUsedUp()) {
			throw new GoneException("사용 횟수를 다 쓴 초대예요.");
		}
		Study study = this.studyRepository.findById(invitation.getStudyId())
			.orElseThrow(() -> new NotFoundException(NOT_FOUND_DETAIL));
		List<StudyRule> rules = this.ruleRepository.findByStudyIdOrderByEffectiveFromAsc(study.getId());
		LocalDate today = StudyTimeline.calendar(rules, study.zoneId()).studyDayOf(now);
		// 공개 미리보기라 예정된 변경은 보여 주지 않고 현재 규칙만 (노출 최소화, S05 판단 기록).
		StudyRule rule = StudyTimeline.ruleOn(rules, today);
		Season season = StudyTimeline.seasonOn(this.seasonRepository.findByStudyIdOrderBySeqAsc(study.getId()), today);
		return new InvitationPreview(study, rule, season, invitation.getExpiresAt());
	}

	/**
	 * 초대를 수락한다 (권한: 로그인, 5.1 처리 순서 3).
	 *
	 * <ol>
	 * <li>스터디 행을 쓰기 잠금으로 읽는다 (결정 제안서 006). 같은 스터디의 수락이 차례로 실행돼서 초대가 여러 개여도 정원을
	 * 넘지 않고, 같은 사람이 동시에 두 번 눌러도 멤버십이 하나다.</li>
	 * <li>이미 멤버면 그대로 성공이다. 사용 횟수를 쓰지 않는다 (5.1 '이미 멤버면 그대로 성공').</li>
	 * <li>만료·철회면 410. 잠금 뒤 최신 사용 횟수로 소진이면 410. 그다음 정원이 찼으면 409. 링크 자체를 쓸 수 없는 경우를
	 * 정원보다 먼저 알린다 (CodeRabbit PR #11 리뷰).</li>
	 * <li>사용 횟수는 {@code used_count < max_uses} 조건부 UPDATE로 늘린다 (5.1 '정원 동시성'). 같은 UPDATE가 철회·만료도
	 * 다시 봐서, 초대를 읽은 뒤 철회가 커밋돼도 그 초대로는 가입하지 못한다. 바뀐 행이 없으면 410.</li>
	 * <li>판정은 가입 다음 스터디 일부터다 (R14).</li>
	 * </ol>
	 */
	@Transactional
	public AcceptedInvitation accept(String token, long userId) {
		Invitation invitation = find(token);
		long studyId = invitation.getStudyId();
		Study study = this.studyRepository.findByIdForUpdate(studyId)
			.orElseThrow(() -> new NotFoundException(NOT_FOUND_DETAIL));
		Optional<Membership> existing = this.membershipRepository.findByStudyIdAndUserIdAndLeftAtIsNull(studyId,
				userId);
		if (existing.isPresent()) {
			return AcceptedInvitation.of(existing.get());
		}
		Instant now = this.clock.instant();
		requireUsable(invitation, now);
		if (this.invitationRepository.findCurrentUsedCount(invitation.getId()) >= invitation.getMaxUses()) {
			throw new GoneException("사용 횟수를 다 쓴 초대예요.");
		}
		if (this.membershipRepository.countByStudyIdAndLeftAtIsNull(studyId) >= StudyLimits.CAPACITY) {
			throw new ConflictException("정원(" + StudyLimits.CAPACITY + "명)이 찼어요.");
		}
		if (this.invitationRepository.incrementUsedCount(invitation.getId(), now) == 0) {
			// 다 썼거나, 앞에서 읽은 뒤에 철회·만료됐다 (조건부 UPDATE가 최신 행으로 다시 본다).
			throw new GoneException("더는 쓸 수 없는 초대예요.");
		}
		Membership membership = this.membershipRepository
			.save(new Membership(studyId, userId, MembershipRole.MEMBER, now, judgedFrom(study, now)));
		return AcceptedInvitation.of(membership);
	}

	/**
	 * 가입 다음 스터디 일 (R14, 5.1 처리 순서 3). 규칙 버전에 걸친 달력으로 센다 (기준 시각이 바뀌는 새 기수 첫날 포함, R2).
	 * 스터디가 시작하기 전에 가입해도 그대로다. 시작 전 날은 기수 밖이라 판정 대상이 아니다 (R9, OD-07). 판정하는 쪽(S09·S12)이
	 * 기수도 함께 본다.
	 */
	private LocalDate judgedFrom(Study study, Instant joinedAt) {
		return StudyTimeline
			.calendar(this.ruleRepository.findByStudyIdOrderByEffectiveFromAsc(study.getId()), study.zoneId())
			.nextStudyDayOf(joinedAt);
	}

	private Invitation find(String token) {
		return this.tokens.hashIfWellFormed(token)
			.flatMap(this.invitationRepository::findByTokenHash)
			.orElseThrow(() -> new NotFoundException(NOT_FOUND_DETAIL));
	}

	private static void requireUsable(Invitation invitation, Instant now) {
		if (invitation.isRevoked()) {
			throw new GoneException("철회된 초대예요.");
		}
		if (invitation.isExpiredAt(now)) {
			throw new GoneException("만료된 초대예요.");
		}
	}

	/**
	 * 만든 초대.
	 * @param id 초대 ID (철회할 때 쓴다)
	 * @param token 토큰 원문. 이때만 보여 준다
	 * @param expiresAt 만료 시각
	 * @param maxUses 사용 횟수
	 */
	public record CreatedInvitation(long id, String token, Instant expiresAt, int maxUses) {
	}

	/**
	 * 초대 미리보기.
	 * @param study 스터디
	 * @param currentRule 지금 적용되는 규칙 버전
	 * @param currentSeason 지금 기수
	 * @param expiresAt 초대 만료 시각
	 */
	public record InvitationPreview(Study study, StudyRule currentRule, Season currentSeason, Instant expiresAt) {
	}

	/**
	 * 수락 결과. 이미 멤버였어도 같은 모양이다.
	 * @param studyId 스터디 ID
	 * @param role 역할
	 * @param judgedFrom 판정을 시작하는 스터디 일
	 */
	public record AcceptedInvitation(long studyId, MembershipRole role, LocalDate judgedFrom) {

		static AcceptedInvitation of(Membership membership) {
			return new AcceptedInvitation(membership.getStudyId(), membership.getRole(), membership.getJudgedFrom());
		}

	}

}
