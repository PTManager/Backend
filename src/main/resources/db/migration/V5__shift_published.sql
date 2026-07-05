-- 근무 편성 발행 상태. 초안(false)은 사장만 보고, 발행(true) 시 직원에게 공개+알림.
-- 기존 근무는 이미 직원에게 노출돼 있으므로 발행됨(TRUE)으로 채운다.
ALTER TABLE shift ADD COLUMN published BOOLEAN NOT NULL DEFAULT TRUE;
