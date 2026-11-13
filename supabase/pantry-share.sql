-- Wellness: shared pantry ("family code"). Run once in the Supabase SQL editor; safe to re-run.
--
-- The server stores only ciphertext. The 16-character code never leaves the phones: `id` is a
-- domain-separated SHA-256 of it, the AES-256-GCM key is derived from it separately
-- (app: lib/PantryShare.kt). Clients reach the data only through the three functions below;
-- the tables themselves are closed to the API roles.
--
-- Abuse limits: per IP 1500 calls/hour (a family shares one home IP) and 10 new codes/day,
-- per code 600 writes/hour,
-- 120 KB per list, 5000 lists in total. A list nobody read or wrote for 7 days is deleted.

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create table if not exists public.pantry_share (
  id text primary key check (id ~ '^[0-9a-f]{64}$'),
  data text not null check (length(data) <= 122880),
  version integer not null default 1,
  used_at timestamptz not null default now()
);
alter table public.pantry_share enable row level security;
revoke all on public.pantry_share from anon, authenticated;

create table if not exists private.rate (
  key text primary key,
  since timestamptz not null,
  hits integer not null
);

-- One hit for `k` in a fixed window; raises once the window holds more than `max_hits`.
-- The failing call rolls back its own increment, so the counter stays at the limit until the
-- window ends.
create or replace function private.hit(k text, max_hits integer, win interval) returns void
language plpgsql security definer set search_path = '' as $$
declare n integer;
begin
  insert into private.rate as r (key, since, hits) values (k, now(), 1)
  on conflict (key) do update set
    hits = case when r.since < now() - win then 1 else r.hits + 1 end,
    since = case when r.since < now() - win then now() else r.since end
  returning r.hits into n;
  if n > max_hits then raise exception 'rate_limited'; end if;
end $$;

-- Client IP as seen by Supabase's edge: Cloudflare sets cf-connecting-ip (clients cannot forge it).
create or replace function private.ip() returns text
language sql stable set search_path = '' as $$
  select coalesce(
    current_setting('request.headers', true)::json->>'cf-connecting-ip',
    split_part(current_setting('request.headers', true)::json->>'x-forwarded-for', ',', 1),
    'unknown')
$$;

create or replace function private.check(p_id text) returns void
language plpgsql security definer set search_path = '' as $$
begin
  perform private.hit('ip:' || private.ip(), 1500, interval '1 hour');
  if p_id is null or p_id !~ '^[0-9a-f]{64}$' then raise exception 'bad_id'; end if;
  -- Expiry does not depend on the cron job below; it only frees the space.
  delete from public.pantry_share where id = p_id and used_at < now() - interval '7 days';
end $$;

-- New shared list; returns its version (1).
create or replace function public.pantry_share_create(p_id text, p_data text) returns integer
language plpgsql security definer set search_path = '' as $$
begin
  perform private.check(p_id);
  perform private.hit('new:' || private.ip(), 10, interval '1 day');
  if (select count(*) from public.pantry_share) >= 5000 then raise exception 'full'; end if;
  insert into public.pantry_share (id, data) values (p_id, p_data);
  return 1;
exception when unique_violation then
  raise exception 'exists';
end $$;

-- The list and its version; no rows when the code is unknown or expired. `data` is null when the
-- caller already has `p_version` (saves egress). Counts as use; `used_at` is rewritten at most
-- hourly to spare writes.
drop function if exists public.pantry_share_get(text);
create or replace function public.pantry_share_get(p_id text, p_version integer default 0) returns table (data text, version integer)
language plpgsql security definer set search_path = '' as $$
begin
  perform private.check(p_id);
  update public.pantry_share s set used_at = now() where s.id = p_id and s.used_at < now() - interval '1 hour';
  return query select case when s.version = p_version then null else s.data end, s.version
    from public.pantry_share s where s.id = p_id;
end $$;

-- Replace the list if it is still at `p_version`. Returns the new version,
-- -1 when someone else wrote first (read and merge again), 0 when the list is gone.
create or replace function public.pantry_share_put(p_id text, p_data text, p_version integer) returns integer
language plpgsql security definer set search_path = '' as $$
declare v integer;
begin
  perform private.check(p_id);
  perform private.hit('put:' || p_id, 600, interval '1 hour');
  update public.pantry_share s set data = p_data, version = s.version + 1, used_at = now()
    where s.id = p_id and s.version = p_version
    returning s.version into v;
  if v is not null then return v; end if;
  return case when exists (select 1 from public.pantry_share where id = p_id) then -1 else 0 end;
end $$;

revoke all on function private.hit(text, integer, interval), private.ip(), private.check(text) from public, anon, authenticated;
revoke all on function public.pantry_share_create(text, text), public.pantry_share_get(text, integer),
  public.pantry_share_put(text, text, integer) from public, authenticated;
grant execute on function public.pantry_share_create(text, text), public.pantry_share_get(text, integer),
  public.pantry_share_put(text, text, integer) to anon;

-- Daily cleanup. If this fails, enable "Cron" (pg_cron) under Database → Extensions and re-run.
create extension if not exists pg_cron;
select cron.schedule('pantry-share-cleanup', '17 3 * * *', $$
  delete from public.pantry_share where used_at < now() - interval '7 days';
  delete from private.rate where since < now() - interval '1 day';
$$);

notify pgrst, 'reload schema';
