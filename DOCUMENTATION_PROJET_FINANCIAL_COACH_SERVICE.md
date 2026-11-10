# Documentation détaillée du projet Financial Coach Service

## 1. Vue d’ensemble du projet

Ce projet est un backend Java Spring Boot conçu pour un service de coaching financier bancaire. Son objectif est d’aider les utilisateurs à gérer leur budget, analyser leurs dépenses, détecter un salaire régulier, contrôler leurs coffres d’épargne, gérer leurs consentements RGPD, et sécuriser les opérations financières avec une logique stricte.

Le projet mélange plusieurs dimensions :

- gestion de profil budgétaire
- gestion d’épargne et de coffres
- détection automatique de revenus
- recommandation de dépenses via IA
- sécurité JWT / OAuth2
- traçabilité avec logs d’audit
- conformité RGPD / droit à l’oubli / consentement
- règles de sécurité métier et anti-IDOR

Ce n’est pas simplement une application de gestion simple : c’est une application orientée banque digitale, donc très stricte sur les règles métier, la confidentialité et l’intégrité des données.

---

## 2. Architecture du projet

Le projet suit une architecture Spring Boot classique :

- `controller/` : API REST
- `service/` : logique métier
- `repository/` : accès aux données
- `model/` : entités JPA / tables de base
- `dto/` : objets d’entrée/sortie
- `exception/` : gestion des erreurs HTTP
- `scheduler/` : tâches planifiées
- `resources/db/migration/` : scripts SQL de migration
- `SecurityConfig.java` : sécurité globale
- `FinancialCoachServiceApplication.java` : point d’entrée

Cette séparation est importante, car elle permet de garder :

- une API qui ne fait que recevoir des requêtes,
- des services qui appliquent la logique métier,
- des repositories qui ont la responsabilité des données,
- des modèles qui reflètent la base de données,
- des DTO qui protègent les entités internes.

Cela évite les erreurs de logique, les fuites d’information et rend le code maintenable.

---

## 3. Point d’entrée de l’application

### `FinancialCoachServiceApplication.java`

```java
@SpringBootApplication
public class FinancialCoachServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinancialCoachServiceApplication.class, args);
    }
}
```

### Explication

- `@SpringBootApplication` : annotation qui active les fonctionnalités Spring Boot.
- Elle active :
  - la configuration automatique,
  - le scan des composants Spring,
  - le démarrage du contexte applicatif,
  - le lancement de l’API web.

### Pourquoi c’est fait

Sans cette classe, le serveur ne demarrerait pas. C’est la porte d’entrée du backend.

---

## 4. Sécurité globale

### `SecurityConfig.java`

Ce fichier configure l’authentification et les autorisations de l’API.

#### Points importants

- `csrf(AbstractHttpConfigurer::disable)` : on désactive le CSRF, car l’API est stateless et protégée via JWT.
- `sessionCreationPolicy(STATELESS)` : aucune session HTTP n’est créée.
- `.requestMatchers("/api/v1/coach/**").hasRole("USER")` : uniquement les utilisateurs avec le rôle `ROLE_USER` peuvent accéder à ces endpoints.
- `oauth2ResourceServer(...)` : le backend valide des tokens JWT émises par un fournisseur externe.

#### Pourquoi c’est important

Le backend manipule des informations sensibles comme :

- revenus,
- objectifs financiers,
- dépenses,
- soldes d’épargne,
- consentements,
- données d’audit.

Il faut donc empêcher tout accès non autorisé. La sécurité JWT garantit que seul un utilisateur authentifié peut agir.

---

## 5. Gestion globale des erreurs

### `GlobalExceptionHandler.java`

Cette classe utilise `@RestControllerAdvice` pour gérer les exceptions de manière centralisée.

#### Méthode principal

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ProblemDetail handleValidationException(MethodArgumentNotValidException ex)
```

#### Ce qu’il fait

- récupère les erreurs de validation du DTO,
- extrait les messages d’erreur,
- transforme cela en `ProblemDetail`,
- retourne un statut HTTP 400 avec un payload structuré.

#### Pourquoi c’est fait

Quand un client envoie un mauvais payload, l’API ne doit pas exploser en erreur technique. Elle doit répondre proprement, avec un message lisible et standardisé.

---

## 6. Fichier de configuration

### `application.yaml`

Ce fichier contient :

- port du serveur : `8082`
- base PostgreSQL : `coach_db`
- JDBC URL et identifiants
- Hibernate et validation
- Flyway
- JWT / JWKS
- configuration OpenAI / Spring AI
- monitoring Prometheus / Actuator

### Pourquoi c’est vital

Le service est une API bancaire. Il a besoin d’un environnement stable, sécurisé et reproductible. La configuration permet de brancher proprement :

- la base de données,
- la sécurité JWT,
- le moteur IA,
- les métriques de monitoring.

---

## 7. Les modèles JPA (entités)

Les classes du package `model/` représentent les tables de la base de données.

### 7.1 `AuditLog.java`

```java
@Entity
@Table(name = "audit_logs")
public class AuditLog {
```

#### Champs

- `id` : identifiant UUID
- `userId` : utilisateur concerné
- `action` : type d’action (`CREATE_VAULT`, `AI_EXPENSE_AUDIT`, etc.)
- `resourceType` : type de ressource (`SMART_VAULT`, `USER_PRIVACY`, etc.)
- `resourceId` : identifiant de la ressource
- `details` : payload JSON contenant les détails
- `ipAddress` : IP du client
- `timestamp` : date et heure

#### Pourquoi ce modèle existe

L’audit est essentiel en finance. Sans suivi, il est impossible de savoir :

- qui a fait quoi,
- quand,
- sur quelle ressource,
- depuis quelle IP.

C’est une base pour la conformité et le support technique.

---

### 7.2 `BudgetProfile.java`

Représente le profil budgétaire.

#### Champs

- `userId`
- `netMonthlyIncome`
- `needsRatio`
- `wantsRatio`
- `savingsRatio`
- `version` : version optimiste pour la concurrence
- `createdAt`, `updatedAt`

#### Méthodes

- `update(...)` : modifie les ratios et la rémunération 

#### Pourquoi c’est fait

Le profil budgétaire sert de base à la répartition de l’argent. Les ratios sont utilisés pour calculer combien l’utilisateur doit consacrer à :

- besoins
- envies
- épargne

---

### 7.3 `SmartVault.java`

Représente un coffre d’épargne.

#### Champs

- `id`
- `userId`
- `vaultName`
- `vaultType`
- `targetAmount`
- `currentBalance`
- `autoTransferDate`
- `hasCardLinked`
- `version`
- `createdAt`, `updatedAt`

#### Enum

```java
public enum VaultType {
    FIXED_EXPENSES,
    SAVINGS,
    GOAL
}
```

#### Méthodes

- `rename(String newName)`
- `credit(BigDecimal amount)`
- `debit(BigDecimal amount)`

#### Pourquoi c’est fait

Le coffre d’épargne est un objectif de sécurité. L’utilisateur peut :

- créer un coffre,
- y affecter un montant cible,
- verser ou retirer de l’argent,
- le renommer,
- empêcher sa suppression si le solde est non vide.

---

### 7.4 `UserConsent.java`

Représente le consentement de l’utilisateur à l’utilisation de ses données IA.

#### Champs

- `userId`
- `aiProcessingConsented`
- `policyVersion`
- `consentedAt`
- `revokedAt`
- `ipAddress`
- `updatedAt`

#### Méthodes

- `grantConsent(String version, String ipAddress)`
- `revokeConsent(String ipAddress)`

#### Pourquoi c’est fait

Le projet utilise une IA pour analyser les dépenses. Par règlement et par principe éthique, il faut recueillir le consentement explicite et enregistré de l’utilisateur. Cela est aussi utile en conformité RGPD.

---

### 7.5 `VaultLedgerEntry.java`

C’est un journal comptable des mouvements de coffre.

#### Champs

- `userId`
- `vaultId`
- `intentId`
- `idempotencyKey`
- `amount`
- `entryType`
- `createdAt`

#### Pourquoi c’est fait

Quand un montant est verrouillé ou transféré, il faut garder une trace claire. Le ledger garantit que même si l’API est relancée, on n’a pas de duplication de mouvement.

---

### 7.6 `VaultLockIntent.java`

Représente une demande de verrouillage temporaire d’un coffre.

#### Enum

```java
public enum IntentStatus {
    PENDING, CONFIRMED, EXPIRED, FAILED
}
```

#### Champs

- `userId`
- `vaultId`
- `amount`
- `scaChallenge`
- `status`
- `expiresAt`
- `createdAt`

#### Méthodes

- `isExpired()`
- `markConfirmed()`

#### Pourquoi c’est fait

Avant d’appliquer un verrouillage de coffre, il faut une étape de validation supplémentaire pour éviter les actions abusives. Cela ressemble à un mécanisme de sécurité à deux niveaux.

---

### 7.7 `BudgetRecommendation.java`

Représente une recommandation de budget calculée pour un utilisateur.

#### Champs

- `userId`
- `summary`
- `recommendationsPayload`
- `potentialMonthlySavings`
- `createdAt`
- `expiresAt`

#### Pourquoi c’est fait

Le système ne se contente pas d’analyser les dépenses. Il préserve le résultat comme une recommandation consultable ou expirable. Cela aide aussi à la suppression automatique des données trop anciennes.

---

## 8. Les DTO

Les fichiers dans `dto/` décrivent les request/response de l’API. Ils servent à valider les données entrantes et à organiser la sortie.

### 8.1 `BudgetProfileDTOs.java`

#### `UpsertProfileRequest`

- `netMonthlyIncome`
- `needsRatio`
- `wantsRatio`
- `savingsRatio`

#### Validation

- `netMonthlyIncome` doit être > 0
- ratios entre 0 et 100
- somme totale = 100

#### `BudgetProfileResponse`

Contient les données renvoyées au client après création ou lecture du profil.

#### Pourquoi c’est fait

Les ratios doivent être cohérents ; sinon le budget ne sera pas réaliste.

---

### 8.2 `BudgetAllocationDTOs.java`

#### `AllocationRequest`

Contient :

- revenu net mensuel
- ratios custom si l’utilisateur les fournit

#### Validation

- si des ratios custom sont renseignés, leur somme doit être 100.

#### `AllocationResponse`

Retourne :

- revenu net
- allocation besoins
- allocation envies
- allocation épargne
- `roundingAdjustment`
- ratios appliqués

#### Pourquoi c’est fait

Il faut calculer le budget selon des règles de répartition logique, avec arrondi et contrôle de somme.

---

### 8.3 `AiExpenseDTOs.java`

#### `WasteAnalysisRequest`

Le client envoie :

- `categorizedExpenses` : map de catégorie -> montant
- `userFinancialGoal` : objectif du client

#### `RawAiPlanResponse`

C’est la réponse brute attendue du LLM.

- `summary`
- `adviceList`
- `detectedSubscriptions`

#### `BudgetPlanResponse`

Sortie validée et propre du service, après nettoyage et vérification. Il contient :

- `coachSummary`
- `verifiedAdvices`
- `activeSubscriptions`
- `potentialMonthlySavings`

#### Pourquoi c’est fait

L’IA peut halluciner. Le système ne veut pas directement renvoyer les résultats bruts. Il faut filtrer, nettoyer et valider avant de les présenter au client.

---

### 8.4 `SalaryDetectionDTOs.java`

#### `TransactionFeedItem`

Représente une transaction de banque :

- id de transaction,
- montant,
- contrepartie,
- libellé,
- date,
- type (`CREDIT` ou `DEBIT`)

#### `DetectedSalaryCandidate`

Résultat d’une détection de salaire régulier.

#### `SalaryProposalResponse`

Proposition finale au client :

- salaire détecté ou non
- montant estimé
- allocation proposée
- message explicatif

#### Pourquoi c’est fait

La détection salaire est automatique, et l’API doit répondre avec un payload clair et exploitable côté front.

---

### 8.5 `SmartVaultDTOs.java`

#### `CreateVaultRequest`

- `vaultName`
- `vaultType`
- `targetAmount`
- `autoTransferDate`

Validation :

- nom obligatoire 2-64 caractères
- type obligatoire
- montant > 0
- jour de transfert entre 1 et 31

#### `RenameVaultRequest`

- `newVaultName`

#### `VaultResponse`

Sortie retournée au frontend après création / lecture / renommage.

#### Pourquoi c’est fait

Le client n’a pas besoin de voir l’inner entity. On lui donne une structure claire et validée.

---

### 8.6 `PrivacyDTOs.java`

#### `ConsentStatusResponse`

Résume si le consentement est actif ou non.

#### `UpdateConsentRequest`

- `consented`
- `policyVersion`

#### `UserDataExportResponse`

Retourne tous les éléments exportables du profil de l’utilisateur :

- consentement,
- profil,
- coffres,
- recommandations

#### Pourquoi c’est fait

La conformité RGPD impose des mécanismes d’export et de suppression.

---

### 8.7 `VaultLockDTOs.java`

#### `LockIntentRequest`

- montant minimum de verrouillage

#### `LockIntentResponse`

Retourne l’intention, le montant, la méthode SCA, expiration.

#### `LockConfirmRequest`

- `intentId`
- `scaCode`

#### `LockConfirmResponse`

Détails du verrouillage confirmé : 

- id de transaction,
- vaultId,
- montant transféré,
- nouveau solde,
- date,
- statut

#### Pourquoi c’est fait

Les opérations de type coffre sont sensibles : elles doivent avoir une étape de sécurité supplémentaire.

---

## 9. Les repositories

Les repositories sont des interfaces Spring Data JPA. Ils simplifient l’accès à la base.

### `BudgetProfileRepository`

```java
Optional<BudgetProfile> findByUserId(UUID userId);
```

#### Pourquoi

Chaque utilisateur a un seul profil budgétaire.

---

### `SmartVaultRepository`

```java
Optional<SmartVault> findByIdAndUserId(UUID id, UUID userId);
List<SmartVault> findAllByUserId(UUID userId);
```

#### Pourquoi

C’est une protection anti-IDOR. L’application vérifie bien que le coffre appartient au bon utilisateur.

---

### `VaultLockIntentRepository`

```java
Optional<VaultLockIntent> findByIdAndUserId(UUID id, UUID userId);
```

#### Pourquoi

Une intention de verrouillage doit être liée à un utilisateur précis.

---

### `VaultLedgerEntryRepository`

```java
Optional<VaultLedgerEntry> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
```

#### Pourquoi

L’API doit bloquer les doublons de transactions. On utilise un identifiant de non-réexécution.

---

### `BudgetRecommendationRepository`

```java
Page<BudgetRecommendation> findAllByUserId(UUID userId, Pageable pageable);
List<BudgetRecommendation> findAllByUserId(UUID userId);
int purgeExpiredRecommendations(Instant now);
deleteAllByUserId(UUID userId);
```

#### Pourquoi

- on veut historique des recommandations,
- on veut purger les données expirées,
- on veut supprimer les données d’un utilisateur lors de l’effacement.

---

### `UserConsentRepository`

```java
Optional<UserConsent> findByUserId(UUID userId);
```

#### Pourquoi

Un utilisateur a un seul statut de consentement.

---

### `AuditLogRepository`

Simple repository générique pour sauvegarder le journal d’audit.

---

## 10. Les services métier

### 10.1 `BudgetProfileService`

#### Méthodes

- `getProfile(UUID userId)`
- `upsertProfile(UUID userId, UpsertProfileRequest req, String clientIp)`
- `mapToResponse(BudgetProfile p)`

#### Détail de `getProfile`

- cherche le profil d’un utilisateur
- si non trouvé, renvoie un profil par défaut

#### Détail de `upsertProfile`

- vérifie si le profil existe
- soit le crée, soit le met à jour
- sauvegarde dans la base
- construit un `AuditLog`
- retourne un `BudgetProfileResponse`

#### Pourquoi cela a été fait

- évite les erreurs si un utilisateur n’a pas encore défini son profil
- garde une logique cohérente de budget par défaut
- enregistre les changements pour auditabilité

---

### 10.2 `BudgetAllocationService`

#### Méthode principal

```java
public AllocationResponse calculateAllocation(UUID userId, AllocationRequest request)
```

#### Détail du calcul

1. récupère `netMonthlyIncome`
2. choisit les ratios selon le profil ou la demande
3. calcule :
   - besoins = revenu * ratioBesoins / 100
   - envies = revenu * ratioEnvies / 100
   - épargne = revenu * ratioEpargne / 100
4. calcule le reliquat = revenu - somme calculée
5. injecte le reliquat dans les besoins pour garder une somme exacte

#### Pourquoi c’est fait

Le budget doit être calculé de façon bancable, avec une somme totale exacte au centime près. Il faut éviter les décalages et les arrondis incohérents.

---

### 10.3 `SalaryDetectionService`

#### Méthode principal

```java
public SalaryProposalResponse analyzeAndPropose(UUID userId, List<TransactionFeedItem> transactions)
```

#### Ce qu’il fait

- filtre les transactions `CREDIT`
- ne conserve que les montants supérieurs à 1500 MAD
- trie les transactions par date décroissante
- regroupe par contrepartie
- cherche des intervalles de dates de 25 à 35 jours
- vérifie qu’il n’y a pas trop de variation entre deux salaires
- si c’est cohérent, il calcule une allocation budgetaire avec `BudgetAllocationService`

#### Pourquoi c’est fait

Les banques ont besoin de détecter automatiquement les revenus réguliers pour proposer des conseils plus précis. Cela permet :

- de savoir si un salaire est régulier,
- d’anticiper le plan budgétaire,
- d’éviter des recommandations arbitraires.

---

### 10.4 `AiExpenseAuditorService`

C’est le service le plus complexe et le plus “IA-driven”.

#### Méthode principal

```java
public BudgetPlanResponse analyzeExpenses(UUID userId, WasteAnalysisRequest request, String clientIp)
```

#### Ce qu’il fait

1. charge le profil de l’utilisateur
2. nettoie et normalise les dépenses
3. construit le prompt système avec les règles strictes
4. appelle Spring AI
5. reçoit une réponse brute
6. valide la réponse avec des filtres Java
7. enregistre un `AuditLog`
8. retourne une réponse nettoyée

#### Méthodes importantes

- `sanitizeAndMaskExpenses(...)`
  - met les catégories en majuscules
  - enlève les caractères non autorisés
  - classifie en `AUTRE` si nécessaire
  - arrondit les montants

- `validateAndFilterResponse(...)`
  - accepte seulement les types de conseils autorisés
  - vérifie que chaque catégorie est bien réelle
  - empêche les montants inventés
  - nettoie le texte de sortie
  - limite à 3 conseils max

- `sanitizeUserInput(...)`
  - enlève les retours chariot et caractères dangereux

- `sanitizeText(...)`
  - supprime HTML, liens, Markdown, gros caractères de formatage

- `generateFallbackResponse(...)`
  - donne une réponse de secours si l’IA échoue

#### Pourquoi c’est fait

L’IA est utile, mais aussi potentiellement dangereuse si elle hallucine. Le service fixe des règles très strictes :

- pas de réponse libre non contrôlée,
- pas de catégories inconnues,
- pas de montants inventés,
- pas de conseils non certifiés,
- pas de markdown ni d’URL,
- pas de recommandations trop nombreuses.

C’est une protection anti-hallucination.

---

### 10.5 `SmartVaultService`

C’est le service central pour la gestion des coffres d’épargne.

#### Méthodes

```java
@Transactional
public VaultResponse createVault(UUID userId, CreateVaultRequest request, String clientIp)
```

**Détail :**

- crée une instance `SmartVault`
- sauvegarde dans la base
- crée un `AuditLog` avec action `CREATE_VAULT`
- retourne le `VaultResponse`

**Pourquoi :**

Un coffre doit être traçable dès sa création, et tout nouvel objectif doit être audité.

---

```java
@Transactional(readOnly = true)
public List<VaultResponse> listUserVaults(UUID userId)
```

**Détail :**

- recherche tous les coffres de l’utilisateur
- les convertit en `VaultResponse`
- retourne la liste

**Pourquoi :**

Le client a besoin d’afficher ses coffres, mais sans pouvoir mélanger ses données avec celles d’un autre utilisateur.

---

```java
@Transactional
public VaultResponse renameVault(UUID userId, UUID vaultId, RenameVaultRequest request, String clientIp)
```

**Détail :**

- vérifie que le coffre appartient bien au bon utilisateur
- si non, lève `EntityNotFoundException`
- modifie le nom via `vault.rename(...)`
- enregistre un log
- retourne le coffre mis à jour

**Pourquoi :**

Le renommage est une action sensible : on veut être sûr que le coffre modifié est bien celui du bon client.

---

```java
@Transactional
public void deleteVault(UUID userId, UUID vaultId, String clientIp)
```

**Détail :**

- vérifie l’appartenance du coffre
- refuse la suppression si le solde est positif
- supprime le coffre
- crée un log d’audit

**Pourquoi :**

Le projet applique une règle bancaire stricte : on ne supprime pas un coffre avec de l’argent encore présent. Cela protège les utilisateurs contre des suppressions accidentelles ou malveillantes.

---

```java
private VaultResponse mapToResponse(SmartVault v)
```

**Détail :**

- transforme l’entité en DTO de sortie

**Pourquoi :**

On ne veut pas exposer directement l’objet JPA au client.

---

### 10.6 `VaultLockService`

#### Méthode `createLockIntent(...)`

- vérifie l’appartenance du coffre
- limite le montant à 50 000 MAD
- crée une intention de verrouillage
- attribue un challenge `SCA-OTP-AUTH-REQUIRED`
- enregistre un log d’audit

#### Pourquoi

La sécurité exige une étape intermédiaire avant de verrouiller un coffre.

---

#### Méthode `confirmLock(...)`

Etapes :

1. vérifie l’idempotency key pour éviter les doubles confirmations
2. consulte l’intention de verrouillage
3. vérifie la correspondance de coffre et le statut `PENDING`
4. vérifie le code SCA
5. crédite le coffre
6. enregistre un `VaultLedgerEntry`
7. marque l’intention comme confirmée
8. crée un log d’audit

#### Pourquoi

Les mouvements de coffre sont sensibles, donc ils doivent être :

- non réexécutables,
- validés par défi 2FA,
- bien liés au bon coffre,
- enregistrés dans un ledger.

---

#### `validateScaChallenge(...)`

Vérifie que le code SCA est non vide et de longueur 6.

#### Pourquoi

C’est le garde-fou de sécurité de type OTP.

---

### 10.7 `PrivacyManagementService`

#### `getConsentStatus(UUID userId)`

Lit le statut du consentement.

#### `updateConsent(...)`

- crée ou récupère le consentement
- active ou révoque selon la demande
- enregistre un log d’audit

#### `exportUserData(UUID userId)`

Rassemble :

- profil,
- coffres,
- consentement,
- historique de recommandations

#### `deleteUserData(UUID userId, String clientIp)`

- vérifie qu’aucun coffre ne contient d’argent
- supprime les recommandations et les coffres
- supprime le profil
- révoque le consentement
- journalise l’effacement

#### Pourquoi

Le projet respecte les droits du client :

- accès à ses données,
- anonymisation/effacement,
- contrôle du consentement,
- conformité réglementaire.

---

## 11. Les contrôleurs HTTP

### `BudgetProfileController`

Endpoints :

- `GET /api/v1/coach/profile`
- `PUT /api/v1/coach/profile`

#### Rôle

Expose le profil utilisateur.

---

### `SmartVaultController`

Endpoints :

- `POST /api/v1/coach/vaults`
- `GET /api/v1/coach/vaults`
- `PATCH /api/v1/coach/vaults/{vaultId}`
- `DELETE /api/v1/coach/vaults/{vaultId}`

#### Rôle

Permet de gérer les coffres d’épargne de l’utilisateur connecté.

---

### `VaultLockController`

Endpoints :

- `POST /api/v1/coach/vaults/{vaultId}/lock-intent`
- `POST /api/v1/coach/vaults/{vaultId}/lock-confirm`

#### Rôle

Gère le verrouillage sécuritaire d’un coffre.

---

### `PrivacyController`

Endpoints :

- `GET /api/v1/coach/me/consent`
- `PUT /api/v1/coach/me/consent`
- `GET /api/v1/coach/me/export`
- `DELETE /api/v1/coach/me/data`

#### Rôle

Exposition du consentement utilisateur et des droits RGPD.

---

## 12. Le rôle des migrations SQL

Fichiers trouvés :

- `V1__init_budget_schema.sql`
- `V2__create_smart_vaults.sql`
- `V3__create_ledger_and_intents.sql`
- `V4__create_consent_and_recommendations.sql`

### Pourquoi Flyway

- versionne les bases de données,
- garantit le même schéma partout,
- réduit les écarts entre dev, test et prod,
- simplifie le déploiement.

---

## 13. Le rôle du scheduler

### `DataRetentionScheduler.java`

```java
@Scheduled(cron = "0 0 2 * * *")
public void purgeExpiredData()
```

#### Ce qu’il fait

- supprime les recommandations expirées chaque jour à 02:00 UTC

#### Pourquoi

Le système stocke des recommandations qui ne doivent pas rester indéfiniment. Cela est cohérent avec les obligations de minimisation de données.

---

## 14. Pourquoi ce projet a été construit comme ça

Ce projet a été construit pour répondre à des besoins réels d’une banque digitale moderne :

- budget personnel assisté par IA,
- détection de salaire régulier,
- gestion d’épargne avec sécurité,
- analyse des dépenses pour réduire les gaspillages,
- respect du RGPD et de la confidentialité,
- traçabilité des opérations,
- protection anti-IDOR,
- mécanismes anti-faute / anti-doublon / anti-erreur financière.

L’architecture va dans le sens d’un système crédible, robuste, conforme et sécuritaire.

---

## 15. Résumé simple

### Ce projet fait à peu près ceci

1. Un utilisateur a un profil budgétaire.
2. Le système analyse ses dépenses.
3. L’IA propose des économies concrètes.
4. Le système détecte ses revenus récurrents.
5. Le système gère ses coffres d’épargne.
6. Les actions sensibles sont auditées.
7. Les droits RGPD sont respectés.
8. Les accès sont sécurisés par JWT et rôle utilisateur.

### C’est important car

La finance doit être :

- précise,
- vérifiable,
- sécurisée,
- conforme,
- traçable,
- et utile pour le client.

---

## 16. Explication très ciblée de `SmartVaultService`

C’est le service qui gère les coffres d’épargne.

### `createVault(...)`

- crée l’objet `SmartVault`
- enregistre en base
- crée un log d’audit
- renvoie un `VaultResponse`

### `listUserVaults(...)`

- récupère les coffres d’un utilisateur donné
- les transforme en DTO

### `renameVault(...)`

- vérifie l’utilisateur et le coffre
- applique le nouveau nom
- enregistre l’action

### `deleteVault(...)`

- interdit la suppression si le solde est positif
- supprime le coffre
- journalise la suppression

### `mapToResponse(...)`

- transforme l’entité en format API

### Pourquoi tout cela existe

Parce qu’un coffre d’épargne est une donnée financière sensible. Il faut :

- contrôler qui peut le voir,
- contrôler qui peut le modifier,
- empêcher la suppression si de l’argent reste dedans,
- tenir un historique de ce qui a été fait.

---

## 17. Conclusion finale

Ce projet est un backend Java Spring Boot sérieux de coaching financier. Il est construit autour de la sécurité, de la logique comptable, de la traçabilité et de la conformité. Chaque classe a une raison précise :

- les `model` décrivent les données,
- les `repository` les stockent,
- les `service` les traitent,
- les `controller` les exposent,
- les `dto` les structurent,
- les `logs` garantissent la sécurité et la traçabilité,
- les `règles métier` évitent les erreurs et les abus,
- l’IA est utilisée avec des garde-fous stricts.

En résumé, c’est un service bancaire intelligent, sécurisé, conforme et orienté utilisateur.
