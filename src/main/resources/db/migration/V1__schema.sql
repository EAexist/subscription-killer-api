--
-- PostgreSQL database dump
--


-- Dumped from database version 17.6 (Debian 17.6-2.pgdg13+1)
-- Dumped by pg_dump version 17.6 (Debian 17.6-2.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: app_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_user (
    id uuid NOT NULL,
    name character varying(255),
    user_role character varying(255),
    CONSTRAINT app_user_user_role_check CHECK (((user_role)::text = ANY ((ARRAY['ADMIN'::character varying, 'USER'::character varying])::text[])))
);


--
-- Name: email_source; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_source (
    is_active boolean NOT NULL,
    id uuid NOT NULL,
    service_provider_id uuid NOT NULL,
    target_address character varying(255) NOT NULL,
    analyzed_message_ids jsonb,
    event_rules jsonb
);


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: google_account; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.google_account (
    analyzed_at timestamp(6) with time zone,
    expires_at timestamp(6) with time zone,
    app_user_id uuid NOT NULL,
    access_token text,
    email character varying(255),
    name character varying(255),
    refresh_token text,
    scope character varying(255),
    subject character varying(255) NOT NULL
);


--
-- Name: service_provider; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.service_provider (
    id uuid NOT NULL,
    display_name character varying(255),
    logo_dev_suffix character varying(255),
    payment_cycle character varying(255),
    subscription_page_url character varying(255),
    website_url character varying(255),
    CONSTRAINT service_provider_payment_cycle_check CHECK (((payment_cycle)::text = ANY ((ARRAY['MONTHLY'::character varying, 'ANNUAL'::character varying, 'BOTH'::character varying, 'NO_PAYMENT'::character varying])::text[])))
);


--
-- Name: service_provider_alias_names; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.service_provider_alias_names (
    service_provider_id uuid NOT NULL,
    alias_name character varying(255),
    locale_key character varying(255) NOT NULL
);


--
-- Name: spring_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.spring_session (
    primary_id character(36) NOT NULL,
    session_id character(36) NOT NULL,
    creation_time bigint NOT NULL,
    last_access_time bigint NOT NULL,
    max_inactive_interval integer NOT NULL,
    expiry_time bigint NOT NULL,
    principal_name character varying(100)
);


--
-- Name: spring_session_attributes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.spring_session_attributes (
    session_primary_id character(36) NOT NULL,
    attribute_name character varying(200) NOT NULL,
    attribute_bytes bytea NOT NULL
);


--
-- Name: subscription; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscription (
    has_subscribed_newsletter_or_ad boolean NOT NULL,
    is_not_sure_if_subscription_is_ongoing boolean NOT NULL,
    registered_since timestamp(6) with time zone,
    subscribed_since timestamp(6) with time zone,
    id uuid NOT NULL,
    service_provider_id uuid NOT NULL,
    google_account_id character varying(255) NOT NULL
);


--
-- Name: app_user app_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);


--
-- Name: email_source email_source_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_source
    ADD CONSTRAINT email_source_pkey PRIMARY KEY (id);


--
-- Name: email_source email_source_target_address_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_source
    ADD CONSTRAINT email_source_target_address_key UNIQUE (target_address);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: google_account google_account_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.google_account
    ADD CONSTRAINT google_account_pkey PRIMARY KEY (subject);


--
-- Name: service_provider_alias_names service_provider_alias_names_locale_key_alias_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_provider_alias_names
    ADD CONSTRAINT service_provider_alias_names_locale_key_alias_name_key UNIQUE (locale_key, alias_name);


--
-- Name: service_provider_alias_names service_provider_alias_names_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_provider_alias_names
    ADD CONSTRAINT service_provider_alias_names_pkey PRIMARY KEY (service_provider_id, locale_key);


--
-- Name: service_provider service_provider_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_provider
    ADD CONSTRAINT service_provider_pkey PRIMARY KEY (id);


--
-- Name: spring_session_attributes spring_session_attributes_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spring_session_attributes
    ADD CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name);


--
-- Name: spring_session spring_session_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spring_session
    ADD CONSTRAINT spring_session_pk PRIMARY KEY (primary_id);


--
-- Name: subscription subscription_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription
    ADD CONSTRAINT subscription_pkey PRIMARY KEY (id);


--
-- Name: subscription uk_report_provider; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription
    ADD CONSTRAINT uk_report_provider UNIQUE (google_account_id, service_provider_id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_alias_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_alias_lookup ON public.service_provider_alias_names USING btree (alias_name);


--
-- Name: idx_email_source_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_email_source_lookup ON public.email_source USING btree (target_address, is_active);


--
-- Name: spring_session_ix1; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX spring_session_ix1 ON public.spring_session USING btree (session_id);


--
-- Name: spring_session_ix2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX spring_session_ix2 ON public.spring_session USING btree (expiry_time);


--
-- Name: spring_session_ix3; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX spring_session_ix3 ON public.spring_session USING btree (principal_name);


--
-- Name: subscription fk1w8ctq9rk197lhd1rtvomjyp5; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription
    ADD CONSTRAINT fk1w8ctq9rk197lhd1rtvomjyp5 FOREIGN KEY (service_provider_id) REFERENCES public.service_provider(id);


--
-- Name: google_account fkc5ldkj3kms04ke9jwyktvy1hr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.google_account
    ADD CONSTRAINT fkc5ldkj3kms04ke9jwyktvy1hr FOREIGN KEY (app_user_id) REFERENCES public.app_user(id);


--
-- Name: subscription fkieobf1jy3qvggwnwgvd23vpx1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription
    ADD CONSTRAINT fkieobf1jy3qvggwnwgvd23vpx1 FOREIGN KEY (google_account_id) REFERENCES public.google_account(subject);


--
-- Name: service_provider_alias_names fkiyof64n4vv5cbkoecjd64q4p; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_provider_alias_names
    ADD CONSTRAINT fkiyof64n4vv5cbkoecjd64q4p FOREIGN KEY (service_provider_id) REFERENCES public.service_provider(id);


--
-- Name: email_source fklotnr9sb2onbyr4qxorovke00; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_source
    ADD CONSTRAINT fklotnr9sb2onbyr4qxorovke00 FOREIGN KEY (service_provider_id) REFERENCES public.service_provider(id);


--
-- Name: spring_session_attributes spring_session_attributes_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spring_session_attributes
    ADD CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id) REFERENCES public.spring_session(primary_id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--


