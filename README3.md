# Financial Coach Service — plan d’amélioration, un point à la fois

## Règle de travail

Ce plan est volontairement séquentiel : **on ne commence qu’une seule étape à la fois**.

Pour chaque étape :

1. Lire son objectif et vérifier les fichiers concernés.
2. Faire uniquement les changements nécessaires pour cet objectif.
3. Exécuter les vérifications indiquées et corriger les échecs liés au changement.
4. Marquer l’étape comme terminée seulement lorsque ses critères d’acceptation sont satisfaits.
5. Passer ensuite à l’étape suivante.

Ne pas essayer de réaliser toute la feuille de route en une seule fois. Si une étape révèle un problème indépendant, le noter pour l’étape correspondante au lieu d’élargir le changement en cours.

## État de départ

- **Étape active : 12 — Automatiser les contrôles de livraison.** Les étapes 10 et 11 ont leurs implémentations locales; leurs validations externes/opérationnelles sont encore en attente. L’étape 8 est implémentée côté service, mais le choix et la validation d’un fournisseur SCA réel restent un prérequis de production.
- **Étapes clôturées : 2, 4, 5 et 7**, avec validation ciblée documentée ci-dessous. L’implémentation de l’étape 6 est terminée, mais la revue des conditions de traitement/rétention du fournisseur IA reste un prérequis de production. L’étape 1 attend la validation complète de sa suite de tests; l’étape 3 attend la confirmation du contrat `sub` et des tests d’intégration PostgreSQL.
- Les changements déjà présents dans le dépôt ne sont pas automatiquement considérés comme validés par ce plan.
- La configuration `src/main/resources/application.yaml` est déjà modifiée dans l’arbre de travail : la lire et préserver ces changements avant toute modification ultérieure.

## Étape 1 — Protéger les endpoints Actuator

**Objectif :** ne plus permettre l’accès anonyme à tous les endpoints `/actuator/**`.

**Périmètre :**

- `src/main/java/com/neobank/coachservice/SecurityConfig.java`
- Les tests de sécurité qui seront ajoutés pour cette règle.

**Règle appliquée :**

1. `/actuator/**` exige le rôle `ADMIN` (claim JWT `role=ADMIN`). Ne pas donner ce rôle aux utilisateurs ordinaires.
2. Aucun endpoint Actuator n’est laissé public. Si l’infrastructure exige une sonde de santé anonyme, confirmer ce besoin et autoriser uniquement le chemin strictement nécessaire avant de l’ajouter. Ne pas rendre publiquement accessibles `info`, `prometheus` ou les détails de santé par commodité.
3. Les tests vérifient le refus des requêtes anonymes, le refus du rôle `USER`, l’accès du rôle `ADMIN` et le maintien de la règle `USER` sur les routes métier.

**Critères d’acceptation :**

- Une requête anonyme à un endpoint Actuator protégé est refusée.
- Un utilisateur authentifié sans le rôle opérateur (si ce rôle est exigé) ne peut pas accéder aux endpoints opérateur.
- Les endpoints métier continuent d’exiger un JWT avec le rôle `USER`.
- Les tests de sécurité et la suite de tests existante passent.
- Aucun endpoint Actuator supplémentaire n’est rendu public.

**État de l’implémentation :** la règle `/actuator/**` exige `ROLE_ADMIN`; les routes `/api/v1/coach/**` continuent d’exiger `ROLE_USER`. Trois tests ciblés vérifient le refus anonyme, l’accès/refus selon le rôle et la règle des routes métier. `.\mvnw.cmd -Dtest=SecurityConfigTest test` réussit.

**Validation générale en attente :** `.\mvnw.cmd test` échoue actuellement dans deux tests préexistants indépendants de cette règle : `FinancialCoachServiceApplicationTests.contextLoads` ne peut pas se connecter à PostgreSQL sur `localhost:5432`, et `AiExpenseAuditorServiceTest` ne fournit pas de mock `ChatClient.Builder`. La validation globale de l’étape 1 reste à refaire dans un environnement adapté; aucun endpoint Actuator anonyme n’a été ajouté.

## Étape 2 — Valider les JWT selon le fournisseur d’identité

**Objectif :** vérifier que le service accepte uniquement les jetons destinés à cette API et émis par l’identité attendue.

**Implémentation et vérification locales réalisées :**

1. Le `JwtDecoder` valide la signature depuis JWKS, l’émetteur (`iss`), la date d’expiration et l’audience (`aud`).
2. `JWT_ISSUER` et `JWT_AUDIENCE` sont des paramètres obligatoires; Docker Compose refuse de démarrer si ces valeurs ne sont pas fournies.
3. Le rôle est lu dans le claim `role`; `USER` est requis pour les routes métier et `ADMIN` pour Actuator. L’étape 3 uniformise l’identifiant propriétaire sur le claim `sub`.
4. Les tests JWT couvrent jeton valide, mauvais issuer, mauvaise audience, expiration et signature invalide; les tests de sécurité vérifient également le refus d’un JWT sans rôle.

**Configuration de déploiement à confirmer auprès du service d’identité :** les valeurs exactes de `iss` et `aud`, le type et le contenu du claim `role`, ainsi que l’URL JWKS utilisée dans chaque environnement. Les valeurs d’exemple des tests sont fictives et ne doivent pas être déployées.

**Validation locale :** `.\mvnw.cmd '-Dtest=JwtDecoderTest,SecurityConfigTest' test` réussit (8 tests). La compilation IDE et l’inspection des fichiers modifiés réussissent. La suite Maven complète n’a pas été relancée pour cette étape; ses deux échecs préexistants restent documentés à l’étape 1.

Le code de validation est implémenté et testé avec un contrat de test. Il faudra configurer les valeurs réelles avant le déploiement; elles ne sont pas disponibles dans le dépôt.

## Étape 3 — Vérifier l’isolation entre utilisateurs

**Objectif :** garantir qu’un utilisateur ne peut jamais consulter ou modifier les données d’un autre utilisateur.

**Décision d’identité :** toutes les routes utilisent `sub` comme identifiant propriétaire, au format UUID. Le claim `userId` ne peut pas remplacer ni surcharger `sub`. Cette règle doit être confirmée avec le fournisseur d’identité, qui doit émettre un `sub` stable au format UUID.

**Implémentation réalisée :**

1. `BudgetProfileController` et `SmartVaultController`, les deux contrôleurs qui privilégiaient le claim `userId`, utilisent maintenant `sub`, comme les autres contrôleurs.
2. Les recherches de profils, consentements et recommandations sont effectuées par `userId`; les coffres et intentions sont recherchés par identifiant de ressource **et** propriétaire.
3. Le ledger de rejeu est recherché avec le propriétaire et la clé d’idempotence.
4. Les tests emploient deux identifiants distincts et un JWT dont `userId` contredit `sub`. Ils vérifient que profil, opérations sur les coffres, recommandations, export, effacement et verrouillage restent associés à `sub`. Ils vérifient également le refus des opérations sur un coffre ou une intention non détenus.

**Validation locale :** `.\mvnw.cmd -Dtest=UserIsolationTest test` réussit (7 tests). La compilation IDE et l’inspection des fichiers modifiés réussissent. Les tests sont des tests unitaires avec dépôts/services simulés; la validation d’intégration contre PostgreSQL demeure nécessaire lorsque la base sera disponible.

**À confirmer avant de clôturer l’étape :** le fournisseur d’identité doit garantir que `sub` est un UUID stable pour le même utilisateur, et que les claims des jetons de production suivent ce contrat. Ajouter ensuite des tests d’intégration PostgreSQL d’accès croisé.

**Critères d’acceptation :** les opérations testées utilisent toutes l’utilisateur authentifié; un utilisateur A ne peut lire, modifier, supprimer ou confirmer les ressources de B. L’étape reste active jusqu’à confirmation du contrat `sub` et aux tests d’intégration.

## Étape 4 — Retirer les secrets et valeurs sensibles par défaut en production

**Objectif :** empêcher le démarrage d’un environnement de production avec des identifiants de développement.

**Implémentation réalisée :**

1. `application.yaml` et `docker-compose.yml` restent utilisables en local avec leurs valeurs de développement; le profil `local` est activé par défaut dans Compose.
2. `application-prod.yaml` exige explicitement base, compte DB, clé IA, URI JWKS, issuer, audience et URL du service de transactions.
3. `ProductionConfigurationValidator` refuse les identifiants DB par défaut, les valeurs factices ciblées, une base sur localhost et les URLs non HTTPS de JWKS, issuer ou service de transactions.
4. `.env.example` documente uniquement des valeurs fictives locales; `.env` et les fichiers `.env.*` sont ignorés par Git, sauf le modèle.
5. `docker-compose.prod.yml` exige les variables de production et force le profil `prod`. Les secrets réels doivent être injectés depuis le gestionnaire de secrets de la plateforme.

**État : terminée pour les contrôles applicatifs et la séparation des profils.** Le développement local reste documenté; le profil de production exige les paramètres critiques et rejette les valeurs de développement testées; aucun secret réel n’est ajouté au dépôt. Le validateur et la compilation passent, et les fichiers YAML se parsèrent correctement. L’exécution de `docker compose ... config` n’a pas pu être faite car Docker n’est pas installé; faire cette vérification dans l’environnement de déploiement avant de lancer les conteneurs.

**Validation :** `.\mvnw.cmd '-Dtest=ProductionConfigurationValidatorTest,JwtDecoderTest,SecurityConfigTest' test` réussit (13 tests). Le build IDE réussit également. Les tests de `ProductionConfigurationValidator` vérifient la configuration explicite, les identifiants par défaut, une base locale, une clé IA factice, les URLs HTTP et une audience de remplacement. Les vraies valeurs de production restent à fournir par la plateforme, elles ne sont pas inventées ici.

## Étape 5 — Borner les entrées et les usages de l’API

**Objectif :** réduire les abus et les entrées excessives avant d’appeler la base de données ou les services externes.

**Implémentation réalisée :**

1. Revenus mensuels bornés à 1 000 000 MAD avec deux décimales au plus; ratios restent entre 0 et 100 et doivent totaliser 100. Les dépenses IA acceptent jusqu’à 9 catégories, 1 000 000 MAD par catégorie et un objectif de 160 caractères.
2. Objectif de coffre borné à 10 000 000 MAD; transfert verrouillé limité au plafond métier existant de 50 000 MAD et à deux décimales. Les noms de coffre, version de politique, code SCA et clé d’idempotence ont aussi des longueurs maximales.
3. `RequestSizeLimitFilter` limite les corps HTTP à 65 536 octets (configurable avec `MAX_HTTP_REQUEST_SIZE`) et renvoie HTTP 413 au-delà.
4. Les paramètres de pagination des recommandations exigent `page` entre 0 et 10 000 et `size` entre 1 et 50.
5. `UserRateLimiter` limite par utilisateur et par instance les analyses IA à 5/minute et la détection de salaire à 10/minute (configurables avec `AI_ANALYSES_PER_MINUTE` et `SALARY_DETECTIONS_PER_MINUTE`). Le contrôle intervient avant tout appel au modèle ou au service de transactions; un dépassement renvoie HTTP 429.

**Validation :** les tests ciblés couvrent les bornes DTO, la taille maximale et le rejet 413, la pagination, les quotas indépendants par utilisateur/opération et l’absence de second appel externe après dépassement. `.\mvnw.cmd '-Dtest=RequestValidationTest,RequestSizeLimitFilterTest,UserRateLimiterTest,ExpensiveOperationRateLimitTest,UserIsolationTest' test` réussit (17 tests); le build IDE réussit également.

**Limite de déploiement :** les compteurs sont en mémoire et propres à chaque instance. En déploiement horizontal, un quota global par utilisateur nécessite de déplacer le contrôle vers une passerelle partagée ou un stockage distribué.

**État : implémentée et validée localement.** Avant de considérer les limites comme globales en production multi-instance, intégrer un limiteur distribué ou une politique équivalente à la passerelle.

## Étape 6 — Appliquer le consentement et minimiser les données envoyées à l’IA

**Objectif :** ne transmettre au fournisseur IA que les données nécessaires et uniquement lorsque le traitement est autorisé.

**Implémentation réalisée :**

1. `AiExpenseAuditorService` vérifie le consentement avant tout appel externe; un consentement absent ou révoqué donne une erreur HTTP 403.
2. Le prompt contient seulement les dépenses agrégées par catégorie normalisée. L’objectif texte et le revenu du profil ne sont pas transmis; ni identifiant utilisateur, adresse IP ou historique transactionnel détaillé n’est inclus.
3. Les tests couvrent consentement absent et révoqué (aucune interaction avec le modèle), ainsi qu’un appel autorisé qui ne transmet que catégorie et montant.

Le client Spring AI OpenAI-compatible est configuré pour le service Groq (`AI_BASE_URL=https://api.groq.com/openai`) et le modèle `openai/gpt-oss-120b`; `GROQ_API_KEY` doit être fournie hors du dépôt. Ici, « OpenAI-compatible » décrit le protocole API, pas le fournisseur. Les conditions de traitement et de conservation des données par Groq restent à confirmer avant production.

**À confirmer avant de clôturer l’étape :** documenter auprès du fournisseur IA et des responsables conformité les régions de traitement, durées de rétention, usages des données et conditions contractuelles. Aucune hypothèse n’est faite sur ces conditions dans le code.

**Critères d’acceptation :** aucun appel IA n’a lieu sans autorisation; seules les données minimales documentées sont transmises.

**Validation locale :** `.\mvnw.cmd '-Dtest=AiExpenseAuditorServiceTest,UserIsolationTest,RequestValidationTest' test` réussit (17 tests). La compilation IDE réussit également. L’étape reste active jusqu’à confirmation documentée des conditions de traitement et de conservation du fournisseur.

## Étape 7 — Durcir le traitement des sorties IA

**Objectif :** considérer chaque réponse du modèle comme non fiable et empêcher les conseils incohérents d’être présentés comme des résultats vérifiés.

**Implémentation réalisée :**

1. Rejeter les conseils dont la catégorie ou le type est inconnu, les montants nuls, négatifs, trop précis ou hors bornes, et les économies qui dépassent le montant cité, la dépense de la catégorie ou le budget restant.
2. Limiter les éléments bruts et ne renvoyer que trois conseils au plus, un par catégorie. Le total est recalculé à partir des seuls conseils réellement retournés.
3. Ne pas exposer les textes générés par le modèle (résumé, recommandation et abonnements non vérifiables). Les conseils texte et le résumé sont générés par des gabarits fixes côté serveur.
4. Une réponse nulle/mal formée ou un appel IA en erreur renvoie un message explicitement indisponible, sans conseil ni montant d’économie.
5. Les tests couvrent les montants négatifs/exagérés, catégories inventées, doublons, plafond de trois conseils, cohérence du total, injections dans les textes IA et le résultat de repli.

**Validation locale :** `.\mvnw.cmd '-Dtest=AiExpenseAuditorServiceTest,UserIsolationTest,RequestValidationTest' test` réussit (22 tests). La compilation IDE réussit également.

**État : implémentée et validée localement.** Aucune sortie textuelle brute du modèle n’est renvoyée et les montants affichés sont vérifiés/recalculés côté serveur.

## Étape 8 — Remplacer la validation SCA simulée

**Objectif :** exiger une véritable preuve de possession/validation avant de confirmer une opération financière.

**Implémentation réalisée :**

1. Le code à six chiffres n’est plus accepté sur sa seule forme : le service appelle un fournisseur via `ScaProvider`; sans fournisseur configuré, la création et la validation échouent de façon fermée.
2. L’API HTTP du fournisseur reçoit l’utilisateur, l’intention, le coffre, le montant et l’expiration au moment de créer le défi. La vérification retransmet ces valeurs et le code afin que le fournisseur valide le même contexte transactionnel.
3. Le défi expire après cinq minutes; l’identifiant du fournisseur est conservé, les échecs sont comptés en base (maximum cinq), l’intention est verrouillée pessimiste pendant la vérification et un défi confirmé/expiré/épuisé ne peut être réutilisé.
4. Les codes sont limités à six chiffres. Les erreurs SCA ont des réponses HTTP dédiées; le code OTP et le jeton fournisseur ne sont pas journalisés.
5. La migration `V5__secure_sca_challenges.sql` ajoute le compteur d’échecs. `VaultLockServiceTest` vérifie le contexte envoyé au fournisseur, l’échec sans crédit et le crédit uniquement après validation positive.

**Contrat du fournisseur HTTP :** `POST /v1/challenges` reçoit `{userId, intentId, vaultId, amount, expiresAt}` et répond `{challengeId}`. `POST /v1/challenges/{challengeId}/verify` reçoit `{userId, intentId, vaultId, amount, code}` et répond `{verified: true|false}`. Les appels ont un délai de connexion de 2 s et de lecture de 3 s.

**Configuration :** en production, `SCA_PROVIDER_BASE_URL` (HTTPS) et `SCA_PROVIDER_API_TOKEN` sont obligatoires. Le fournisseur choisi doit être approuvé, authentifier l’utilisateur et imposer l’usage unique et l’expiration du défi; l’adaptateur attend le contrat ci-dessus. Il n’existe pas de fournisseur ni d’identifiants configurés dans ce dépôt.

**Validation locale :** `.\mvnw.cmd '-Dtest=VaultLockServiceTest,ProductionConfigurationValidatorTest,UserIsolationTest,RequestValidationTest' test` réussit (25 tests). Les appels réels au fournisseur et la concurrence PostgreSQL doivent être vérifiés après le choix du fournisseur.

**État : implémentation du service terminée; étape toujours active.** La clôture production reste bloquée jusqu’au choix/approbation d’un fournisseur compatible et à la validation de son intégration réelle. Ce prérequis de production demeure distinct du travail demandé sur l’étape 9.

## Étape 9 — Renforcer l’intégrité du ledger et l’idempotence

**Objectif :** garantir qu’une opération concurrente ou répétée ne crédite pas deux fois un coffre.

**Implémentation réalisée :**

1. Une clé d’idempotence est sérialisée par verrou consultatif PostgreSQL; l’intention et le coffre sont verrouillés pendant la confirmation. `@Version` reste une protection additionnelle.
2. Un rejeu n’est accepté que si utilisateur, coffre, intention, clé et type de transaction correspondent à l’écriture existante. Réutiliser la clé pour une autre opération retourne HTTP 409.
3. La migration `V6__enforce_ledger_transaction_integrity.sql` lie le ledger à une intention du même utilisateur, coffre et montant, exige un coffre existant et interdit une clé vide. Les contraintes uniques existantes empêchent toujours le double ledger par intention ou clé utilisateur.
4. Les tests vérifient le rejeu exact, le rejet d’une clé détournée sans crédit, ainsi que l’existence des verrous utilisés.

**Validation locale :** `VaultLockConcurrencyIntegrationTest` s’exécute sur PostgreSQL et vérifie que deux confirmations simultanées avec la même clé produisent un seul crédit et une seule ligne ledger. PostgreSQL a validé les six migrations et le schéma est à jour. La suite complète `.\mvnw.cmd test` réussit : 53 tests, aucune erreur.

**État : terminée et validée localement.** Le fournisseur réel de l’étape 8 reste un prérequis distinct avant mise en production.

## Étape 10 — Compléter la confidentialité et la rétention des données

**Objectif :** couvrir les données personnelles avec une politique d’export, de conservation et d’effacement vérifiable.

**Inventaire et comportement implémentés :**

| Données | Contenu | Export | Effacement |
|---|---|---|---|
| Profil budgétaire | Revenu mensuel et ratios | Inclus | Supprimé |
| Consentement IA | Version, état, dates et IP | Inclus | Supprimé |
| Recommandations | Résumé, contenu JSON, économies et dates | Inclus | Supprimées à la demande; purge quotidienne après `expires_at` |
| Coffres | Nom, type, objectif, solde et paramètres | Inclus | Supprimés s’ils n’ont pas d’écriture ledger; sinon conservés avec métadonnées neutralisées |
| Intentions SCA | Montant, état, tentatives et dates | Inclus, sans défi SCA | Supprimées si elles n’ont pas d’écriture ledger; sinon conservées avec le défi remplacé par `ERASED` |
| Ledger | Montant, type, coffre, intention et date | Inclus, sans clé d’idempotence | Conservé; identifiant utilisateur pseudonymisé et clé d’idempotence remplacée |
| Audit | Action, ressource, détails, IP et date | Inclus | Conservé sous identifiant pseudonyme; détails, IP et identifiant de ressource effacés |
| Limiteur de débit | Compteurs en mémoire indexés par utilisateur et opération | Non exporté (donnée temporaire opérationnelle) | Entrées de l’utilisateur supprimées à l’effacement; sinon fenêtre d’une minute |

Un effacement est refusé avec HTTP 409 si un coffre a un solde positif. Les opérations d’effacement sont transactionnelles. L’export et l’effacement ne couvrent que les données de ce microservice, pas celles du fournisseur d’identité, du fournisseur SCA ou du fournisseur IA.

**Validation juridique encore requise avant de clôturer l’étape :** aucun délai de conservation ni fondement juridique des traces ledger/audit/consentement n’est fixé par le dépôt. Le responsable conformité/DPO et le conseil juridique doivent valider les durées, exceptions, exigences de preuve du consentement et conditions de suppression chez les fournisseurs externes. Le code conserve actuellement le ledger et l’audit pseudonymisés sans durée automatique; ce choix technique ne constitue pas une décision de conformité.

**Tests ajoutés :** l’export couvre profil, consentement, coffres, recommandations, intentions, ledger et audit; les tests PostgreSQL vérifient le blocage avec solde positif, l’effacement des données supprimables et la pseudonymisation des écritures conservées.

**État : implémentation technique et tests PostgreSQL validés. L’étape reste active jusqu’à l’approbation des règles de conservation par les responsables compétents.**

## Étape 11 — Uniformiser erreurs, audit et alertes de sécurité

**Objectif :** rendre les incidents détectables sans exposer de secrets ni de détails internes.

**Travail à faire :**

1. Définir des réponses d’erreur cohérentes pour validation, refus d’accès, absence de ressource et indisponibilité externe.
2. Enregistrer les événements de sécurité nécessaires, avec minimisation des données personnelles.
3. Ne jamais journaliser mots de passe, JWT, clés API, OTP ou prompts contenant des données financières.
4. Ajouter métriques/alertes sur les échecs d’authentification, de SCA, les erreurs fournisseur et l’usage IA anormal.

**Implémentation réalisée :**

- Les erreurs REST utilisent `ProblemDetail` et un code stable : validation/requête 400, authentification 401, autorisation 403, ressource 404, conflit 409, limite 429 et fournisseur indisponible 503. Les erreurs non prévues retournent un message générique avec un identifiant de corrélation, sans exception, token, URL fournisseur ou contenu de requête dans la réponse.
- Les refus 401/403 de Spring Security ont le même format et incrémentent `coach.security.authentication.failures`.
- Les métriques Micrometer sont exposées via `/actuator/prometheus`, protégé par le rôle ADMIN. Les compteurs ont des tags limités : résultat d’authentification, résultat SCA, nom fixe du fournisseur, résultat IA et type d’opération limité.
- Les refus SCA, les limites de tentatives, les indisponibilités SCA/transaction/IA, les sorties IA invalides, les fallbacks IA et les limites de débit sont mesurés. Les journaux d’erreur IA et profil ne contiennent plus d’identifiant utilisateur; aucun prompt, OTP, JWT ou clé API n’est journalisé.
- Des règles d’alerte Prometheus initiales sont dans `monitoring/prometheus-alerts.yml` pour les échecs d’authentification/SCA, erreurs fournisseurs, sorties IA invalides, taux de fallback et événements de limite de débit.

Les seuils du fichier Prometheus sont des valeurs de départ à ajuster aux volumes réels. L’équipe d’exploitation doit configurer le scrape de l’endpoint protégé, importer les règles et relier Prometheus à Alertmanager/au canal d’astreinte; ce dépôt ne déploie pas l’infrastructure de monitoring.

**Validation :** tests unitaires couvrant le masquage des erreurs fournisseur et les compteurs, tests MockMvc pour le format 401/403, tests IA et suite Maven complète.

**État : code et règles d’alerte implémentés. La validation opérationnelle (scrape, routage Alertmanager et réglage des seuils) est à effectuer dans l’environnement de déploiement. La validation juridique de la rétention de l’étape 10 reste également en attente.**

## Étape 12 — Automatiser les contrôles de livraison

**Objectif :** détecter automatiquement les régressions et vulnérabilités avant intégration.

**Contrôles prévus :**

1. Ajouter une CI exécutant les tests Maven et la construction du projet.
2. Ajouter l’analyse des dépendances Maven et des images Docker, avec traitement des alertes.
3. Produire un inventaire des dépendances (SBOM) et contrôler les versions des images de base.
4. Faire échouer le pipeline si les tests de sécurité critiques ou les contrôles requis échouent.

**Implémentation réalisée :**

- `.github/workflows/ci-security.yml` s’exécute sur pull request, push vers `main`, lancement manuel et chaque semaine.
- Le pipeline démarre PostgreSQL 17, exécute la suite Maven et package l’application. Les identifiants de test sont non sensibles et limités au runner CI.
- CycloneDX produit un SBOM JSON. OWASP Dependency-Check échoue pour une vulnérabilité CVSS 7 ou plus; le workflow publie les rapports de dépendances.
- Trivy analyse le dépôt (dépendances, secrets et configuration) ainsi que l’image construite; une vulnérabilité HIGH/CRITICAL ou un finding applicable fait échouer le job.
- Le contrôle de base vérifie que les images Docker ont un tag versionné (pas `latest`) et que l’image PostgreSQL est `17-alpine`. Dependabot propose des mises à jour Maven, Docker et GitHub Actions.

**Configuration requise dans GitHub :** le secret `NVD_API_KEY` est recommandé pour éviter les limites du flux NVD; sans lui, l’analyse reste activée mais peut être plus lente ou limitée par NVD. Pour bloquer réellement les fusions, définir le contrôle `Verify, scan, and build` comme exigence de protection de branche sur `main`. Les rapports sont conservés comme artefacts 14 jours.

**État : implémentation terminée localement. Le lancement GitHub Actions et le réglage de la protection de branche doivent être vérifiés après publication du workflow dans le dépôt.**

## Comment reprendre le travail

Reprendre à l’étape marquée **active**. Une fois son objectif et ses critères d’acceptation remplis, mettre à jour l’état dans ce document, puis passer à l’étape suivante. Ne pas regrouper plusieurs étapes sous prétexte qu’elles touchent au même fichier : la séparation permet de tester et de comprendre chaque amélioration indépendamment.
