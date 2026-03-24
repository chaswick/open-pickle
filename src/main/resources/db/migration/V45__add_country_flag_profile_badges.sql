-- Seed always-available country flag profile badges using MIT licensed art copied into static assets.
-- Assumption: this list is a curated top-100 by population / broad recognition.
CREATE TEMPORARY TABLE tmp_country_flag_profile_badges (
    slug VARCHAR(80) NOT NULL,
    storage_key VARCHAR(64) NOT NULL,
    image_url VARCHAR(512) NOT NULL,
    title VARCHAR(96) NOT NULL,
    summary VARCHAR(140) NOT NULL,
    unlock_condition VARCHAR(512) NOT NULL,
    display_order INT NOT NULL,
    PRIMARY KEY (slug),
    UNIQUE KEY uk_tmp_country_flag_profile_badges_storage_key (storage_key)
) ENGINE=InnoDB;

INSERT INTO tmp_country_flag_profile_badges (slug, storage_key, image_url, title, summary, unlock_condition, display_order)
VALUES
    ('flag-india', 'flag-india', '/images/profile_badges/flags/India.png', 'India', 'Country flag', 'Always available. Represent India in the standings.', 1001),
    ('flag-china', 'flag-china', '/images/profile_badges/flags/China.png', 'China', 'Country flag', 'Always available. Represent China in the standings.', 1002),
    ('flag-united-states', 'flag-united-states', '/images/profile_badges/flags/United-States.png', 'United States', 'Country flag', 'Always available. Represent United States in the standings.', 1003),
    ('flag-indonesia', 'flag-indonesia', '/images/profile_badges/flags/Indonesia.png', 'Indonesia', 'Country flag', 'Always available. Represent Indonesia in the standings.', 1004),
    ('flag-pakistan', 'flag-pakistan', '/images/profile_badges/flags/Pakistan.png', 'Pakistan', 'Country flag', 'Always available. Represent Pakistan in the standings.', 1005),
    ('flag-nigeria', 'flag-nigeria', '/images/profile_badges/flags/Nigeria.png', 'Nigeria', 'Country flag', 'Always available. Represent Nigeria in the standings.', 1006),
    ('flag-brazil', 'flag-brazil', '/images/profile_badges/flags/Brazil.png', 'Brazil', 'Country flag', 'Always available. Represent Brazil in the standings.', 1007),
    ('flag-bangladesh', 'flag-bangladesh', '/images/profile_badges/flags/Bangladesh.png', 'Bangladesh', 'Country flag', 'Always available. Represent Bangladesh in the standings.', 1008),
    ('flag-russia', 'flag-russia', '/images/profile_badges/flags/Russia.png', 'Russia', 'Country flag', 'Always available. Represent Russia in the standings.', 1009),
    ('flag-mexico', 'flag-mexico', '/images/profile_badges/flags/Mexico.png', 'Mexico', 'Country flag', 'Always available. Represent Mexico in the standings.', 1010),
    ('flag-japan', 'flag-japan', '/images/profile_badges/flags/Japan.png', 'Japan', 'Country flag', 'Always available. Represent Japan in the standings.', 1011),
    ('flag-ethiopia', 'flag-ethiopia', '/images/profile_badges/flags/Ethiopia.png', 'Ethiopia', 'Country flag', 'Always available. Represent Ethiopia in the standings.', 1012),
    ('flag-philippines', 'flag-philippines', '/images/profile_badges/flags/Philippines.png', 'Philippines', 'Country flag', 'Always available. Represent Philippines in the standings.', 1013),
    ('flag-egypt', 'flag-egypt', '/images/profile_badges/flags/Egypt.png', 'Egypt', 'Country flag', 'Always available. Represent Egypt in the standings.', 1014),
    ('flag-vietnam', 'flag-vietnam', '/images/profile_badges/flags/Vietnam.png', 'Vietnam', 'Country flag', 'Always available. Represent Vietnam in the standings.', 1015),
    ('flag-democratic-republic-of-the-congo', 'flag-democratic-republic-of-the-congo', '/images/profile_badges/flags/Democratic-Republic-of-the-Congo.png', 'Democratic Republic of the Congo', 'Country flag', 'Always available. Represent Democratic Republic of the Congo in the standings.', 1016),
    ('flag-iran', 'flag-iran', '/images/profile_badges/flags/Iran.png', 'Iran', 'Country flag', 'Always available. Represent Iran in the standings.', 1017),
    ('flag-turkey', 'flag-turkey', '/images/profile_badges/flags/Turkey.png', 'Turkey', 'Country flag', 'Always available. Represent Turkey in the standings.', 1018),
    ('flag-germany', 'flag-germany', '/images/profile_badges/flags/Germany.png', 'Germany', 'Country flag', 'Always available. Represent Germany in the standings.', 1019),
    ('flag-thailand', 'flag-thailand', '/images/profile_badges/flags/Thailand.png', 'Thailand', 'Country flag', 'Always available. Represent Thailand in the standings.', 1020),
    ('flag-united-kingdom', 'flag-united-kingdom', '/images/profile_badges/flags/United-Kingdom.png', 'United Kingdom', 'Country flag', 'Always available. Represent United Kingdom in the standings.', 1021),
    ('flag-france', 'flag-france', '/images/profile_badges/flags/France.png', 'France', 'Country flag', 'Always available. Represent France in the standings.', 1022),
    ('flag-tanzania', 'flag-tanzania', '/images/profile_badges/flags/Tanzania.png', 'Tanzania', 'Country flag', 'Always available. Represent Tanzania in the standings.', 1023),
    ('flag-south-africa', 'flag-south-africa', '/images/profile_badges/flags/South-Africa.png', 'South Africa', 'Country flag', 'Always available. Represent South Africa in the standings.', 1024),
    ('flag-italy', 'flag-italy', '/images/profile_badges/flags/Italy.png', 'Italy', 'Country flag', 'Always available. Represent Italy in the standings.', 1025),
    ('flag-kenya', 'flag-kenya', '/images/profile_badges/flags/Kenya.png', 'Kenya', 'Country flag', 'Always available. Represent Kenya in the standings.', 1026),
    ('flag-myanmar', 'flag-myanmar', '/images/profile_badges/flags/Myanmar.png', 'Myanmar', 'Country flag', 'Always available. Represent Myanmar in the standings.', 1027),
    ('flag-colombia', 'flag-colombia', '/images/profile_badges/flags/Colombia.png', 'Colombia', 'Country flag', 'Always available. Represent Colombia in the standings.', 1028),
    ('flag-south-korea', 'flag-south-korea', '/images/profile_badges/flags/South-Korea.png', 'South Korea', 'Country flag', 'Always available. Represent South Korea in the standings.', 1029),
    ('flag-sudan', 'flag-sudan', '/images/profile_badges/flags/Sudan.png', 'Sudan', 'Country flag', 'Always available. Represent Sudan in the standings.', 1030),
    ('flag-uganda', 'flag-uganda', '/images/profile_badges/flags/Uganda.png', 'Uganda', 'Country flag', 'Always available. Represent Uganda in the standings.', 1031),
    ('flag-spain', 'flag-spain', '/images/profile_badges/flags/Spain.png', 'Spain', 'Country flag', 'Always available. Represent Spain in the standings.', 1032),
    ('flag-algeria', 'flag-algeria', '/images/profile_badges/flags/Algeria.png', 'Algeria', 'Country flag', 'Always available. Represent Algeria in the standings.', 1033),
    ('flag-iraq', 'flag-iraq', '/images/profile_badges/flags/Iraq.png', 'Iraq', 'Country flag', 'Always available. Represent Iraq in the standings.', 1034),
    ('flag-argentina', 'flag-argentina', '/images/profile_badges/flags/Argentina.png', 'Argentina', 'Country flag', 'Always available. Represent Argentina in the standings.', 1035),
    ('flag-afghanistan', 'flag-afghanistan', '/images/profile_badges/flags/Afghanistan.png', 'Afghanistan', 'Country flag', 'Always available. Represent Afghanistan in the standings.', 1036),
    ('flag-yemen', 'flag-yemen', '/images/profile_badges/flags/Yemen.png', 'Yemen', 'Country flag', 'Always available. Represent Yemen in the standings.', 1037),
    ('flag-canada', 'flag-canada', '/images/profile_badges/flags/Canada.png', 'Canada', 'Country flag', 'Always available. Represent Canada in the standings.', 1038),
    ('flag-angola', 'flag-angola', '/images/profile_badges/flags/Angola.png', 'Angola', 'Country flag', 'Always available. Represent Angola in the standings.', 1039),
    ('flag-ukraine', 'flag-ukraine', '/images/profile_badges/flags/Ukraine.png', 'Ukraine', 'Country flag', 'Always available. Represent Ukraine in the standings.', 1040),
    ('flag-morocco', 'flag-morocco', '/images/profile_badges/flags/Morocco.png', 'Morocco', 'Country flag', 'Always available. Represent Morocco in the standings.', 1041),
    ('flag-poland', 'flag-poland', '/images/profile_badges/flags/Poland.png', 'Poland', 'Country flag', 'Always available. Represent Poland in the standings.', 1042),
    ('flag-uzbekistan', 'flag-uzbekistan', '/images/profile_badges/flags/Uzbekistan.png', 'Uzbekistan', 'Country flag', 'Always available. Represent Uzbekistan in the standings.', 1043),
    ('flag-malaysia', 'flag-malaysia', '/images/profile_badges/flags/Malaysia.png', 'Malaysia', 'Country flag', 'Always available. Represent Malaysia in the standings.', 1044),
    ('flag-mozambique', 'flag-mozambique', '/images/profile_badges/flags/Mozambique.png', 'Mozambique', 'Country flag', 'Always available. Represent Mozambique in the standings.', 1045),
    ('flag-ghana', 'flag-ghana', '/images/profile_badges/flags/Ghana.png', 'Ghana', 'Country flag', 'Always available. Represent Ghana in the standings.', 1046),
    ('flag-peru', 'flag-peru', '/images/profile_badges/flags/Peru.png', 'Peru', 'Country flag', 'Always available. Represent Peru in the standings.', 1047),
    ('flag-saudi-arabia', 'flag-saudi-arabia', '/images/profile_badges/flags/Saudi-Arabia.png', 'Saudi Arabia', 'Country flag', 'Always available. Represent Saudi Arabia in the standings.', 1048),
    ('flag-madagascar', 'flag-madagascar', '/images/profile_badges/flags/Madagascar.png', 'Madagascar', 'Country flag', 'Always available. Represent Madagascar in the standings.', 1049),
    ('flag-cameroon', 'flag-cameroon', '/images/profile_badges/flags/Cameroon.png', 'Cameroon', 'Country flag', 'Always available. Represent Cameroon in the standings.', 1050),
    ('flag-cote-d-ivoire', 'flag-cote-d-ivoire', '/images/profile_badges/flags/Cote-dIvoire.png', 'Cote d''Ivoire', 'Country flag', 'Always available. Represent Cote d''Ivoire in the standings.', 1051),
    ('flag-nepal', 'flag-nepal', '/images/profile_badges/flags/Nepal.png', 'Nepal', 'Country flag', 'Always available. Represent Nepal in the standings.', 1052),
    ('flag-venezuela', 'flag-venezuela', '/images/profile_badges/flags/Venezuela.png', 'Venezuela', 'Country flag', 'Always available. Represent Venezuela in the standings.', 1053),
    ('flag-niger', 'flag-niger', '/images/profile_badges/flags/Niger.png', 'Niger', 'Country flag', 'Always available. Represent Niger in the standings.', 1054),
    ('flag-australia', 'flag-australia', '/images/profile_badges/flags/Australia.png', 'Australia', 'Country flag', 'Always available. Represent Australia in the standings.', 1055),
    ('flag-north-korea', 'flag-north-korea', '/images/profile_badges/flags/North-Korea.png', 'North Korea', 'Country flag', 'Always available. Represent North Korea in the standings.', 1056),
    ('flag-syria', 'flag-syria', '/images/profile_badges/flags/Syria.png', 'Syria', 'Country flag', 'Always available. Represent Syria in the standings.', 1057),
    ('flag-mali', 'flag-mali', '/images/profile_badges/flags/Mali.png', 'Mali', 'Country flag', 'Always available. Represent Mali in the standings.', 1058),
    ('flag-burkina-faso', 'flag-burkina-faso', '/images/profile_badges/flags/Burkina-Faso.png', 'Burkina Faso', 'Country flag', 'Always available. Represent Burkina Faso in the standings.', 1059),
    ('flag-sri-lanka', 'flag-sri-lanka', '/images/profile_badges/flags/Sri-Lanka.png', 'Sri Lanka', 'Country flag', 'Always available. Represent Sri Lanka in the standings.', 1060),
    ('flag-malawi', 'flag-malawi', '/images/profile_badges/flags/Malawi.png', 'Malawi', 'Country flag', 'Always available. Represent Malawi in the standings.', 1061),
    ('flag-zambia', 'flag-zambia', '/images/profile_badges/flags/Zambia.png', 'Zambia', 'Country flag', 'Always available. Represent Zambia in the standings.', 1062),
    ('flag-kazakhstan', 'flag-kazakhstan', '/images/profile_badges/flags/Kazakhstan.png', 'Kazakhstan', 'Country flag', 'Always available. Represent Kazakhstan in the standings.', 1063),
    ('flag-chad', 'flag-chad', '/images/profile_badges/flags/Chad.png', 'Chad', 'Country flag', 'Always available. Represent Chad in the standings.', 1064),
    ('flag-chile', 'flag-chile', '/images/profile_badges/flags/Chile.png', 'Chile', 'Country flag', 'Always available. Represent Chile in the standings.', 1065),
    ('flag-romania', 'flag-romania', '/images/profile_badges/flags/Romania.png', 'Romania', 'Country flag', 'Always available. Represent Romania in the standings.', 1066),
    ('flag-somalia', 'flag-somalia', '/images/profile_badges/flags/Somalia.png', 'Somalia', 'Country flag', 'Always available. Represent Somalia in the standings.', 1067),
    ('flag-senegal', 'flag-senegal', '/images/profile_badges/flags/Senegal.png', 'Senegal', 'Country flag', 'Always available. Represent Senegal in the standings.', 1068),
    ('flag-guatemala', 'flag-guatemala', '/images/profile_badges/flags/Guatemala.png', 'Guatemala', 'Country flag', 'Always available. Represent Guatemala in the standings.', 1069),
    ('flag-netherlands', 'flag-netherlands', '/images/profile_badges/flags/Netherlands.png', 'Netherlands', 'Country flag', 'Always available. Represent Netherlands in the standings.', 1070),
    ('flag-ecuador', 'flag-ecuador', '/images/profile_badges/flags/Ecuador.png', 'Ecuador', 'Country flag', 'Always available. Represent Ecuador in the standings.', 1071),
    ('flag-cambodia', 'flag-cambodia', '/images/profile_badges/flags/Cambodia.png', 'Cambodia', 'Country flag', 'Always available. Represent Cambodia in the standings.', 1072),
    ('flag-zimbabwe', 'flag-zimbabwe', '/images/profile_badges/flags/Zimbabwe.png', 'Zimbabwe', 'Country flag', 'Always available. Represent Zimbabwe in the standings.', 1073),
    ('flag-guinea', 'flag-guinea', '/images/profile_badges/flags/Guinea.png', 'Guinea', 'Country flag', 'Always available. Represent Guinea in the standings.', 1074),
    ('flag-benin', 'flag-benin', '/images/profile_badges/flags/Benin.png', 'Benin', 'Country flag', 'Always available. Represent Benin in the standings.', 1075),
    ('flag-rwanda', 'flag-rwanda', '/images/profile_badges/flags/Rwanda.png', 'Rwanda', 'Country flag', 'Always available. Represent Rwanda in the standings.', 1076),
    ('flag-burundi', 'flag-burundi', '/images/profile_badges/flags/Burundi.png', 'Burundi', 'Country flag', 'Always available. Represent Burundi in the standings.', 1077),
    ('flag-tunisia', 'flag-tunisia', '/images/profile_badges/flags/Tunisia.png', 'Tunisia', 'Country flag', 'Always available. Represent Tunisia in the standings.', 1078),
    ('flag-bolivia', 'flag-bolivia', '/images/profile_badges/flags/Bolivia.png', 'Bolivia', 'Country flag', 'Always available. Represent Bolivia in the standings.', 1079),
    ('flag-belgium', 'flag-belgium', '/images/profile_badges/flags/Belgium.png', 'Belgium', 'Country flag', 'Always available. Represent Belgium in the standings.', 1080),
    ('flag-haiti', 'flag-haiti', '/images/profile_badges/flags/Haiti.png', 'Haiti', 'Country flag', 'Always available. Represent Haiti in the standings.', 1081),
    ('flag-jordan', 'flag-jordan', '/images/profile_badges/flags/Jordan.png', 'Jordan', 'Country flag', 'Always available. Represent Jordan in the standings.', 1082),
    ('flag-cuba', 'flag-cuba', '/images/profile_badges/flags/Cuba.png', 'Cuba', 'Country flag', 'Always available. Represent Cuba in the standings.', 1083),
    ('flag-south-sudan', 'flag-south-sudan', '/images/profile_badges/flags/South-Sudan.png', 'South Sudan', 'Country flag', 'Always available. Represent South Sudan in the standings.', 1084),
    ('flag-dominican-republic', 'flag-dominican-republic', '/images/profile_badges/flags/Dominican-Republic.png', 'Dominican Republic', 'Country flag', 'Always available. Represent Dominican Republic in the standings.', 1085),
    ('flag-czech-republic', 'flag-czech-republic', '/images/profile_badges/flags/Czech-Republic.png', 'Czech Republic', 'Country flag', 'Always available. Represent Czech Republic in the standings.', 1086),
    ('flag-greece', 'flag-greece', '/images/profile_badges/flags/Greece.png', 'Greece', 'Country flag', 'Always available. Represent Greece in the standings.', 1087),
    ('flag-portugal', 'flag-portugal', '/images/profile_badges/flags/Portugal.png', 'Portugal', 'Country flag', 'Always available. Represent Portugal in the standings.', 1088),
    ('flag-azerbaijan', 'flag-azerbaijan', '/images/profile_badges/flags/Azerbaijan.png', 'Azerbaijan', 'Country flag', 'Always available. Represent Azerbaijan in the standings.', 1089),
    ('flag-sweden', 'flag-sweden', '/images/profile_badges/flags/Sweden.png', 'Sweden', 'Country flag', 'Always available. Represent Sweden in the standings.', 1090),
    ('flag-honduras', 'flag-honduras', '/images/profile_badges/flags/Honduras.png', 'Honduras', 'Country flag', 'Always available. Represent Honduras in the standings.', 1091),
    ('flag-united-arab-emirates', 'flag-united-arab-emirates', '/images/profile_badges/flags/United-Arab-Emirates.png', 'United Arab Emirates', 'Country flag', 'Always available. Represent United Arab Emirates in the standings.', 1092),
    ('flag-hungary', 'flag-hungary', '/images/profile_badges/flags/Hungary.png', 'Hungary', 'Country flag', 'Always available. Represent Hungary in the standings.', 1093),
    ('flag-tajikistan', 'flag-tajikistan', '/images/profile_badges/flags/Tajikistan.png', 'Tajikistan', 'Country flag', 'Always available. Represent Tajikistan in the standings.', 1094),
    ('flag-belarus', 'flag-belarus', '/images/profile_badges/flags/Belarus.png', 'Belarus', 'Country flag', 'Always available. Represent Belarus in the standings.', 1095),
    ('flag-austria', 'flag-austria', '/images/profile_badges/flags/Austria.png', 'Austria', 'Country flag', 'Always available. Represent Austria in the standings.', 1096),
    ('flag-papua-new-guinea', 'flag-papua-new-guinea', '/images/profile_badges/flags/Papua-New-Guinea.png', 'Papua New Guinea', 'Country flag', 'Always available. Represent Papua New Guinea in the standings.', 1097),
    ('flag-serbia', 'flag-serbia', '/images/profile_badges/flags/Serbia.png', 'Serbia', 'Country flag', 'Always available. Represent Serbia in the standings.', 1098),
    ('flag-israel', 'flag-israel', '/images/profile_badges/flags/Israel.png', 'Israel', 'Country flag', 'Always available. Represent Israel in the standings.', 1099),
    ('flag-switzerland', 'flag-switzerland', '/images/profile_badges/flags/Switzerland.png', 'Switzerland', 'Country flag', 'Always available. Represent Switzerland in the standings.', 1100);

INSERT INTO trophy_art (storage_key, image_url, image_bytes, created_at, updated_at)
SELECT seed.storage_key, seed.image_url, NULL, NOW(6), NOW(6)
FROM tmp_country_flag_profile_badges seed
WHERE NOT EXISTS (
    SELECT 1 FROM trophy_art existing WHERE existing.storage_key = seed.storage_key
);

INSERT INTO trophy (
    season_id, title, summary, unlock_condition, rarity, status, slug, art_id, badge_art_id,
    ai_provider, generation_seed, prompt, unlock_expression, is_limited, is_repeatable,
    evaluation_scope, is_default_template, story_mode_tracker, story_mode_key, max_claims,
    display_order, badge_selectable_by_all, generated_at, updated_at, regeneration_count
)
SELECT
    NULL, seed.title, seed.summary, seed.unlock_condition, 'COMMON', 'GENERATED', seed.slug, NULL, art.id,
    'static-profile-badge', seed.storage_key, 'MIT licensed country flag profile badge asset.', NULL, 0, 0,
    'USER', 0, 0, NULL, NULL, seed.display_order, 1, NOW(6), NOW(6), 0
FROM tmp_country_flag_profile_badges seed
JOIN trophy_art art ON art.storage_key = seed.storage_key
WHERE NOT EXISTS (
    SELECT 1 FROM trophy existing WHERE existing.slug = seed.slug
);

DROP TEMPORARY TABLE tmp_country_flag_profile_badges;
