-- 퇴근 체크 시각. 실제 근무시간 기반 인건비 정산에 사용.
ALTER TABLE shift ADD COLUMN checked_out_at TIMESTAMPTZ;
