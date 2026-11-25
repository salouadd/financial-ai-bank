
# README20 - Financial Coach Service

Microservice bancaire Spring Boot permettant de gérer un profil budgétaire, calculer des allocations 50/30/20, analyser les dépenses avec une IA, détecter les revenus récurrents, gérer des coffres d'épargne et appliquer les exigences de sécurité, de traçabilité et de confidentialité.

## 1. Informations techniques

- Java 21
- Spring Boot 3.5.16
- Spring Security OAuth2 Resource Server / JWT
- Spring Data JPA
- PostgreSQL 17
- Flyway
- Spring AI OpenAI compatible
- Jakarta Validation
- Resilience4j
- Spring Actuator et Prometheus
- Maven
- Docker / Docker Compose

Port local par défaut : `8082`

## 2. Fonctionnalités implémentées

### 2.1 Profil budgétaire

- Consultation du profil de l'utilisateur.
- Création ou mise à jour du revenu mensuel net.
- Configuration des ratios Besoins, Envies et Épargne.
- Validation stricte : les trois ratios doivent totaliser exactement `100`.
- Revenu positif avec maximum de `1 000 000 MAD`.
- Optimistic locking via le champ `version`.
- Journalisation des créations et mises à jour.

Endpoints :

| Méthode | Route | Description |
|---|---|---|
| `GET` | `/api/v1/coach/profile` | Lire le profil |
| `PUT` | `/api/v1/coach/profile` | Créer ou mettre à jour le profil |

### 2.2 Allocation budgétaire

- Calcul de l'allocation 50/30/20.
- Utilisation automatique des ratios du profil.
- Possibilité de fournir des ratios personnalisés.
- Contrôle que les ratios personnalisés totalisent `100`.
- Gestion du reliquat d'arrondi réinjecté dans l'allocation.
- Aucun stockage obligatoire : le calcul peut être demandé à la volée.

Endpoint :

| Méthode | Route | Description |
|---|---|---|
| `POST` | `/api/v1/coach/allocate` | Calculer une allocation |

Corps minimal :

```json
{
  "netMonthlyIncome": 10000
}
```

Corps avec ratios personnalisés :

```json
{
  "netMonthlyIncome": 10000,
  "customNeedsRatio": 50,
  "customWantsRatio": 20,
  "customSavingsRatio": 30
}
```

### 2.3 Détection du salaire

- Récupération des entrées financières récentes auprès du `Transaction Service`.
- Transmission du JWT utilisateur au service partenaire.
- Détection d'une source de revenus récurrents.
- Estimation du salaire probable et du niveau de confiance.
- Proposition d'une allocation budgétaire basée sur les revenus détectés.
- Limitation du nombre de détections par utilisateur.

Endpoint :

| Méthode | Route | Description |
|---|---|---|
| `GET` | `/api/v1/coach/salary/detect` | Détecter un salaire récurrent |

### 2.4 Audit IA des dépenses

- Analyse d'un historique de dépenses catégorisées.
- Appel à un modèle compatible OpenAI, par exemple Groq.
- Structure de sortie contrôlée.
- Vérification des montants proposés par rapport aux dépenses réellement fournies.
- Assainissement des recommandations afin de limiter les hallucinations.
- Historisation des recommandations avec date d'expiration.
- Limitation du nombre d'analyses IA par utilisateur.
- Blocage si le consentement IA de l'utilisateur n'est pas actif.

Endpoint :

| Méthode | Route | Description |
|---|---|---|
| `POST` | `/api/v1/coach/analyze-budget` | Analyser les dépenses |

Corps :

```json
{
  "categorizedExpenses": {
    "LOGEMENT": 3000,
    "ALIMENTATION": 1800,
    "TRANSPORT": 600,
    "ABONNEMENTS": 250
  },
  "userFinancialGoal": "Constituer une épargne de secours"
}
```

Limites actuelles :

- maximum 9 catégories ;
- nom de catégorie limité à 40 caractères ;
- objectif limité à 160 caractères ;
- montant maximum par catégorie : `1 000 000 MAD`.

### 2.5 Coffres intelligents

Types de coffres :

- `FIXED_EXPENSES`
- `SAVINGS`
- `GOAL`

Fonctionnalités :

- Création d'un coffre.
- Renommage d'un coffre.
- Consultation des coffres de l'utilisateur.
- Suppression d'un coffre vide.
- Objectif d'épargne configurable.
- Jour automatique de transfert configurable de 1 à 31.
- Interdiction bancaire de rattacher une carte à un coffre `SAVINGS`.
- Optimistic locking sur les coffres.
- Restrictions sur les soldes négatifs et les montants invalides.

Endpoints :

| Méthode | Route | Description |
|---|---|---|
| `POST` | `/api/v1/coach/vaults` | Créer un coffre |
| `GET` | `/api/v1/coach/vaults` | Lister les coffres |
| `PATCH` | `/api/v1/coach/vaults/{vaultId}` | Renommer un coffre |
| `DELETE` | `/api/v1/coach/vaults/{vaultId}` | Supprimer un coffre |

### 2.6 Verrouillage sécurisé d'un coffre

- Création d'une intention de verrouillage.
- Montant compris entre `1.00` et `50 000.00 MAD`.
- Défi SCA limité à 5 minutes.
- Confirmation avec code à 6 chiffres.
- Limitation à 5 tentatives SCA.
- Expiration et invalidation des intentions.
- Vérification du propriétaire de l'intention, du coffre et de l'utilisateur.
- Confirmation strictement idempotente avec `Idempotency-Key`.
- Ledger comptable immuable.
- Invariance vérifiée entre intention, coffre, utilisateur et montant.

Endpoints :

| Méthode | Route | Description |
|---|---|---|
| `POST` | `/api/v1/coach/vaults/{vaultId}/lock-intent` | Créer une intention |
| `POST` | `/api/v1/coach/vaults/{vaultId}/lock-confirm` | Confirmer le verrouillage |

Exemple d'intention :

```json
{
  "amount": 500
}
```

Exemple de confirmation :

```json
{
  "intentId": "UUID_RETOURNE_PAR_LOCK_INTENT",
  "scaCode": "123456"
}
```

Entête obligatoire :

```text
Idempotency-Key: test-confirm-lock-001
```

### 2.7 Historique des recommandations

- Pagination et tri des recommandations par date décroissante.
- Taille de page limitée à 50.
- Historique filtré automatiquement par l'utilisateur JWT.
- Purge quotidienne à 02:00 UTC des recommandations expirées.

Endpoint :

| Méthode | Route | Description |
|---|---|---|
| `GET` | `/api/v1/coach/recommendations?page=0&size=10` | Lire l'historique |

### 2.8 Confidentialité, consentement et droit à l'effacement

- Lecture du consentement IA.
- Mise à jour du consentement IA.
- Export complet des données utilisateur.
- Effacement des données personnelles.
- Blocage de l'effacement si un coffre contient encore un solde.
- Anonymisation des données comptables conservées pour intégrité.
- Suppression des profils, consentements, recommandations et autres données personnelles directement effaçables.
- Anonymisation finale des journaux d'audit.

Endpoints :

| Méthode | Route | Description |
|---|---|---|
| `GET` | `/api/v1/coach/me/consent` | Lire le consentement |
| `PUT` | `/api/v1/coach/me/consent` | Modifier le consentement |
| `GET` | `/api/v1/coach/me/export` | Exporter les données |
| `DELETE` | `/api/v1/coach/me/data` | Demander l'effacement |

## 3. Sécurité

### 3.1 Authentification JWT

- API stateless.
- Validation du JWT via JWKS.
- Validation de l'émetteur `iss`.
- Validation de l'audience `aud`.
- Validation de l'expiration.
- Construction des rôles Spring depuis le claim `role`.
- Les routes `/api/v1/coach/**` exigent `ROLE_USER`.
- Les routes Actuator exigent `ROLE_ADMIN`.
- Le claim `sub` doit être un UUID et représente l'identité propriétaire.

### 3.2 Isolation des données

- Aucun endpoint métier n'utilise un `userId` transmis librement par le client.
- Toutes les opérations sont filtrées avec le `UUID` issu du JWT.
- Vérification explicite des relations coffre-utilisateur lors des opérations sensibles.
- Historique des recommandations limité à l'utilisateur courant.

### 3.3 Validation et traitement des requêtes

- Validation Jakarta sur tous les DTO d'entrée.
- Taille maximale des requêtes : 65 536 octets par défaut.
- Réponse HTTP structurée `application/problem+json`.
- Erreurs métier centralisées et normalisées.
- Pas de détails internes dans les réponses d'erreur publiques.

### 3.4 Rate limiting

- Limite configurable pour les analyses IA.
- Limite configurable pour les détections de salaire.
- Comptage par utilisateur et opération.
- Fenêtre glissante en mémoire d'une minute.
- Valeur par défaut :
  - 5 analyses IA/minute ;
  - 10 détections de salaire/minute.

### 3.5 SCA et idempotence

- Délai d'expiration du défi SCA de 5 minutes.
- Code SCA composé de 6 chiffres.
- Maximum de 5 tentatives échouées.
- SCA désactivable uniquement pour les environnements locaux via `SCA_PROVIDER_ENABLED=false`.
- Header d'idempotence limité à 128 caractères alphanumériques, points, tirets et tildes.
- Conflit détecté si une clé d'idempotence est réutilisée pour une autre opération.

### 3.6 Journalisation et monitoring

- Audit des créations, modifications, suppressions et opérations sensibles.
- Adresse IP enregistrée lorsque disponible.
- Métriques Prometheus :
  - échecs d'authentification ;
  - événements SCA ;
  - erreurs des fournisseurs ;
  - événements IA ;
  - dépassements de rate limit.
- Endpoint `/actuator/health`.
- Endpoint `/actuator/prometheus` réservé aux administrateurs.

### 3.7 Sécurité du profil de production

Le profil `prod` empêche un démarrage incorrect si :

- PostgreSQL n'est pas déclaré ;
- l'utilisateur JDBC est `postgres` ;
- le mot de passe est vide ou contient un placeholder ;
- la clé IA est vide ou factice ;
- les URL ne sont pas HTTPS ;
- le JWKS est localhost ;
- l'audience JWT est absente ;
- les URL ou le token SCA sont invalides.

Le conteneur Docker s'exécute avec l'utilisateur système non-root `10001`.

### 3.8 Sécurité de la chaîne logicielle

- Vérifications OWASP Dependency-Check.
- Génération SBOM CycloneDX.
- Analyse Trivy des sources et de la configuration.
- Analyse Trivy de l'image Docker.
- Vérification des images Docker versionnées dans le pipeline CI.

## 4. Base de données et migrations

Flyway exécute automatiquement les migrations présentes dans `src/main/resources/db/migration`.

| Migration | Contenu |
|---|---|
| `V1` | Profils budgétaires et logs d'audit |
| `V2` | Coffres intelligents |
| `V3` | Ledger et intentions de verrouillage |
| `V4` | Consentements, recommandations et index |
| `V5` | Durée des défis SCA et tentatives échouées |
| `V6` | Contraintes transactionnelles du ledger |
| `V7` | Anonymisation compatible avec les contraintes FK différées |

Règle JPA : `spring.jpa.hibernate.ddl-auto=validate`. Hibernate ne modifie jamais automatiquement le schéma en production.

## 5. Prérequis

### Windows / Maven

- Java 21
- Maven 3.9 ou wrapper Maven inclus
- PostgreSQL 17 ou Docker Desktop
- Identity Service accessible pour émettre les JWT
- Clé IA facultative pour les analyses réelles
- Transaction Service facultatif selon les fonctionnalités testées

### Docker

- Docker Desktop
- Docker Compose

## 6. Démarrage local

### Option A : PostgreSQL Docker + application Maven

Depuis PostgreSQL 17 :

```powershell
docker run --name coach-postgres `
  -e POSTGRES_DB=coach_db `
  -e POSTGRES_USER=postgres `
  -e POSTGRES_PASSWORD=local-only-change-me `
  -p 5432:5432 `
  -d postgres:17-alpine
```

Configurer les variables PowerShell pour la session :

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/coach_db"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="local-only-change-me"
$env:JWKS_URI="http://localhost:8081/.well-known/jwks.json"
$env:JWT_ISSUER="https://identity.example.test"
$env:JWT_AUDIENCE="financial-coach-service"
$env:GROQ_API_KEY="mock-key-for-dev"
```

Démarrer :

```powershell
.\mvnw.cmd spring-boot:run
```

### Option B : Docker Compose

```powershell
Copy-Item .env.example .env
```

Modifier `.env`, puis démarrer :

```powershell
docker compose up --build
```

Arrêter :

```powershell
docker compose down
```

Supprimer aussi les données PostgreSQL :

```powershell
docker compose down -v
```

Le service sera disponible sur `http://localhost:8082`.

## 7. Commandes de test

### Compiler et exécuter tous les tests

```powershell
.\mvnw.cmd clean test
```

### Vérification complète Maven

```powershell
.\mvnw.cmd clean verify
```

### Générer le SBOM

```powershell
.\mvnw.cmd org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom
```

Le fichier est généré sous `target/bom.json`.

### Vérifier les dépendances avec OWASP

```powershell
.\mvnw.cmd org.owasp:dependency-check-maven:12.1.0:check
```

### Construire l'image Docker

```powershell
docker build -t financial-coach-service:local .
```

### Lancer l'image construite

```powershell
docker run --rm -p 8082:8082 `
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://host.docker.internal:5432/coach_db" `
  -e SPRING_DATASOURCE_USERNAME="postgres" `
  -e SPRING_DATASOURCE_PASSWORD="local-only-change-me" `
  -e JWKS_URI="http://host.docker.internal:8081/.well-known/jwks.json" `
  -e JWT_ISSUER="https://identity.example.test" `
  -e JWT_AUDIENCE="financial-coach-service" `
  financial-coach-service:local
```

## 8. Vérification rapide

Healthcheck :

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8082/actuator/health"
```

Le healthcheck est réservé à `ROLE_ADMIN` dans la configuration Spring Security. Pour l'utiliser dans un navigateur ou avec curl, fournissez un JWT administrateur.

Première connexion Identity Service :

```powershell
$loginBody = @{
  email = "coach-test@example.test"
  password = "mot-de-passe-temporaire"
} | ConvertTo-Json

$login = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/users/login" `
  -ContentType "application/json" `
  -Body $loginBody

$token = $login.accessToken
```

Ne placez jamais un vrai JWT dans un fichier versionné ou dans les logs partagés.

Créer les entêtes :

```powershell
$headers = @{
  Authorization = "Bearer $token"
  "Content-Type" = "application/json"
}
```

Créer ou mettre à jour le profil :

```powershell
$profileBody = @{
  netMonthlyIncome = 10000
  needsRatio = 50
  wantsRatio = 30
  savingsRatio = 20
} | ConvertTo-Json

Invoke-RestMethod -Method Put `
  -Uri "http://localhost:8082/api/v1/coach/profile" `
  -Headers $headers `
  -Body $profileBody
```

Lire le profil :

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8082/api/v1/coach/profile" `
  -Headers $headers
```

Calculer une allocation :

```powershell
$allocationBody = @{
  netMonthlyIncome = 10000
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8082/api/v1/coach/allocate" `
  -Headers $headers `
  -Body $allocationBody
```

Créer un coffre :

```powershell
$vaultBody = @{
  vaultName = "Fonds urgence"
  vaultType = "SAVINGS"
  targetAmount = 30000
  autoTransferDate = 5
} | ConvertTo-Json

$vault = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8082/api/v1/coach/vaults" `
  -Headers $headers `
  -Body $vaultBody

$vault
```

Lister les coffres :

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8082/api/v1/coach/vaults" `
  -Headers $headers
```

Créer une intention de verrouillage :

```powershell
$intentBody = @{
  amount = 500
} | ConvertTo-Json

$intent = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8082/api/v1/coach/vaults/$($vault.id)/lock-intent" `
  -Headers $headers `
  -Body $intentBody

$intent
```

Confirmer le verrouillage :

```powershell
$confirmHeaders = $headers.Clone()
$confirmHeaders["Idempotency-Key"] = "test-confirm-lock-001"

$confirmBody = @{
  intentId = $intent.intentId
  scaCode = "123456"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8082/api/v1/coach/vaults/$($vault.id)/lock-confirm" `
  -Headers $confirmHeaders `
  -Body $confirmBody
```

Analyser les dépenses :

```powershell
$analysisBody = @{
  categorizedExpenses = @{
    LOGEMENT = 3000
    ALIMENTATION = 1800
    TRANSPORT = 600
  }
  userFinancialGoal = "Constituer une épargne de secours"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8082/api/v1/coach/analyze-budget" `
  -Headers $headers `
  -Body $analysisBody
```

Consulter ou modifier le consentement IA :

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8082/api/v1/coach/me/consent" `
  -Headers $headers

$consentBody = @{
  consented = $true
  policyVersion = "v1"
} | ConvertTo-Json

Invoke-RestMethod -Method Put `
  -Uri "http://localhost:8082/api/v1/coach/me/consent" `
  -Headers $headers `
  -Body $consentBody
```

Exporter les données :

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8082/api/v1/coach/me/export" `
  -Headers $headers
```

Demander l'effacement :

```powershell
Invoke-RestMethod -Method Delete `
  -Uri "http://localhost:8082/api/v1/coach/me/data" `
  -Headers $headers
```

L'effacement échouera avec un conflit tant qu'un coffre associé possède un solde supérieur à zéro.

## 9. Structure principale du projet

```text
src/main/java/com/neobank/coachservice/
  controller/       API REST
  dto/              Entrées et sorties validées
  exception/        Exceptions métier et RFC 7807
  model/            Entités JPA
  repository/       Accès PostgreSQL
  sca/              Provider SCA désactivable ou HTTP
  scheduler/        Purge des données expirées
  security/         Gestionnaires d'erreurs Spring Security
  service/          Logique métier
  monitoring/       Métriques Prometheus

src/main/resources/
  application.yaml
  application-prod.yaml
  db/migration/

src/test/java/
  Tests unitaires et intégrations de sécurité, SCA, vaults et confidentialité
```

## 10. Tests automatisés

Les tests couvrent notamment :

- démarrage du contexte Spring ;
- validation du JWT et du JWKS ;
- validation de l'audience ;
- isolation des utilisateurs ;
- configuration de sécurité ;
- limite de taille des requêtes ;
- validation des DTO ;
- gestion centralisée des erreurs ;
- purge des données expirées ;
- fonctionnalités IA ;
- export et effacement des données ;
- rate limiting ;
- concurrence sur les coffres ;
- confirmations SCA ;
- intégrité et idempotence des opérations.
