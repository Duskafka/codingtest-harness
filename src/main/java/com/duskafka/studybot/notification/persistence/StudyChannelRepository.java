package com.duskafka.studybot.notification.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스터디 디스코드 채널 설정을 넣고 바꾼다 (S14 만들 것 1·4). 쓰기는 네이티브 쿼리만 쓴다 ({@link NotificationRepository}와 같은 이유).
 * 웹훅 URL은 이미 암호화한 문자열로만 받는다.
 */
public interface StudyChannelRepository extends JpaRepository<StudyChannel, Long> {

	/**
	 * 웹훅을 등록한다(행이 없으면 만든다). 켜고, 마지막 오류를 지우고, 리비전을 1 올린다.
	 *
	 * @return 바꾼 행 수
	 */
	@Transactional
	@Modifying
	@Query(value = """
			insert into studybot.study_channels
				(study_id, discord_webhook_url_enc, discord_enabled, discord_last_error, discord_revision, updated_at)
			values (:studyId, :ciphertext, true, null, 1, :now)
			on conflict (study_id) do update
			set discord_webhook_url_enc = excluded.discord_webhook_url_enc, discord_enabled = true, discord_last_error = null,
				discord_revision = studybot.study_channels.discord_revision + 1, updated_at = excluded.updated_at
			""", nativeQuery = true)
	int register(@Param("studyId") long studyId, @Param("ciphertext") String ciphertext, @Param("now") Instant now);

	/**
	 * 웹훅을 해제한다: 주소를 지우고 끄고 리비전을 1 올린다. 행이 없으면 아무것도 하지 않는다.
	 *
	 * @return 바꾼 행 수
	 */
	@Transactional
	@Modifying
	@Query(value = """
			update studybot.study_channels
			set discord_webhook_url_enc = null, discord_enabled = false, discord_last_error = null,
				discord_revision = discord_revision + 1, updated_at = :now
			where study_id = :studyId
			""", nativeQuery = true)
	int unregister(@Param("studyId") long studyId, @Param("now") Instant now);

	/**
	 * 웹훅이 지워져(404) 끈다. 리비전이 그대로이고 켜져 있을 때만이다. 그사이 방장이 다시 등록했거나 해제했으면 바꾸지 않는다.
	 *
	 * @return 바꾼 행 수
	 */
	@Transactional
	@Modifying
	@Query(value = """
			update studybot.study_channels set discord_enabled = false, discord_last_error = :error, updated_at = :now
			where study_id = :studyId and discord_revision = :revision and discord_enabled
			""", nativeQuery = true)
	int disable(@Param("studyId") long studyId, @Param("revision") long revision, @Param("error") String error,
			@Param("now") Instant now);

	/**
	 * 마지막 오류만 남긴다(끄지 않음). 리비전이 그대로일 때만이다.
	 *
	 * @return 바꾼 행 수
	 */
	@Transactional
	@Modifying
	@Query(value = """
			update studybot.study_channels set discord_last_error = :error, updated_at = :now
			where study_id = :studyId and discord_revision = :revision
			""", nativeQuery = true)
	int recordError(@Param("studyId") long studyId, @Param("revision") long revision, @Param("error") String error,
			@Param("now") Instant now);

	/** 이 스터디에 지금 게시할 수 있는지 (켜져 있고 주소가 있음). 행이 없으면 거짓이다. */
	@Query(value = """
			select exists (select 1 from studybot.study_channels
				where study_id = :studyId and discord_enabled and discord_webhook_url_enc is not null)
			""", nativeQuery = true)
	boolean isPostable(@Param("studyId") long studyId);

	/** 지금 키가 아닌 키로 암호화한 행들 (키 교체 뒤 재암호화, 9.6). {@code currentPrefix}는 {@code v1:{지금 키 ID}:}다. */
	@Query(value = """
			select * from studybot.study_channels
			where discord_webhook_url_enc is not null and discord_webhook_url_enc not like :currentPrefix || '%'
			order by study_id
			""", nativeQuery = true)
	List<StudyChannel> findNotEncryptedWith(@Param("currentPrefix") String currentPrefix);

	/**
	 * 암호문을 바꾼다. 읽은 뒤 그사이 다른 값으로 바뀌었으면(재등록 등) 바꾸지 않는다.
	 *
	 * @return 바꾼 행 수
	 */
	@Transactional
	@Modifying
	@Query(value = """
			update studybot.study_channels set discord_webhook_url_enc = :replacement
			where study_id = :studyId and discord_webhook_url_enc = :expected
			""", nativeQuery = true)
	int replaceCiphertext(@Param("studyId") long studyId, @Param("expected") String expected,
			@Param("replacement") String replacement);

}
