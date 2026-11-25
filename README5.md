# README5 — Requêtes pour utiliser Financial Coach Service

Ce guide montre, dans l’ordre, comment démarrer le service et appeler ses routes avec PowerShell sous Windows. Les exemples utilisent `http://localhost:8082`; en production, utiliser l’URL HTTPS fournie par la plateforme.

> **Sécurité :** ne collez jamais une clé Groq, un vrai JWT ou un code OTP dans un dépôt, un README ou un ticket. La clé Groq partagée dans une conversation doit être révoquée et remplacée. Les exemples ci-dessous n’incluent aucune clé réelle.

## 1. Prérequis et démarrage local

- Docker Desktop et Docker Compose installés, ou Java 21 et PostgreSQL configurés.
- Un JWT **valide** émis par le service d’identité, avec un `sub` au format UUID et le rôle `USER`. Ce microservice ne fournit pas de route de connexion ni de génération de JWT.
- Pour l’analyse IA, une clé Groq valide dans `.env`.
- Pour la détection de salaire, un service de transactions opérationnel.
- Pour confirmer un verrouillage, un fournisseur SCA opérationnel et configuré; aucun faux code ne permet de contourner cette vérification.

Depuis la racine du dépôt, créer le fichier local s’il n’existe pas déjà :

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
```

Dans `.env`, remplacer la valeur factice par une clé Groq obtenue auprès de Groq :

```dotenv
GROQ_API_KEY=votre_nouvelle_cle_groq
AI_BASE_URL=https://api.groq.com/openai
AI_MODEL=openai/gpt-oss-120b
```

L’identifiant `openai/gpt-oss-120b` est le nom du modèle disponible via Groq; ce n’est pas le produit Grok de xAI. Ne pas ajouter la clé à `.env.example`.

Vérifier que `JWT_ISSUER` et `JWT_AUDIENCE` dans `.env` correspondent à l’identité utilisée par votre environnement, puis démarrer les conteneurs :

```powershell
docker compose up --build -d
docker compose ps
docker compose logs --tail 100 financial-coach-service
```

L’application écoute normalement sur `http://localhost:8082`. Vérifier les logs de démarrage et l’application des migrations Flyway. Pour arrêter les services sans supprimer les données PostgreSQL :

```powershell
docker compose down
```

> `.env.example` contient des valeurs de développement fictives. Un démarrage avec ces valeurs ne constitue pas un test de connexion JWT, Groq, SCA ou du service de transactions. Ne lancez pas la configuration locale sur des données de production.

## 2. Préparer les requêtes authentifiées

Dans la fenêtre PowerShell où vous effectuez les appels, définir l’URL et saisir un JWT `USER` valide. La saisie avec `Read-Host -AsSecureString` évite de l’afficher à l’écran :

```powershell
$baseUrl = "http://localhost:8082"
$secureToken = Read-Host "JWT de test (role USER)" -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureToken)
try {
    $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
}
$headers = @{ Authorization = "Bearer $token" }
```

Le jeton doit avoir un `sub` UUID, un issuer (`iss`) égal à `JWT_ISSUER`, l’audience (`aud`) contenant `JWT_AUDIENCE`, une signature vérifiable avec `JWKS_URI`, une date valide et le rôle `USER`. Un jeton expiré, fictif ou destiné à une autre API sera refusé.

Pour appeler une route protégée, le patron est :

```powershell
Invoke-RestMethod -Uri "$baseUrl/api/v1/coach/profile" -Method Get -Headers $headers
```

Une réponse `401` indique généralement que le jeton est absent, invalide ou expiré; `403` signifie que le rôle ou le consentement requis manque.

### Obtenir un JWT utilisateur depuis identity-service

Le compte montré dans les commandes précédentes a le rôle `ROLE_ADMIN`. Utilise un compte de test séparé, dont le rôle est `ROLE_USER`, pour tester les routes métier du coach. Le JWT doit contenir `sub` comme UUID, et ses claims `iss` et `aud` doivent correspondre à la configuration du coach. Un statut KYC `PENDING_KYC` n’est pas à lui seul un rôle `USER`; les autorisations du coach sont déterminées par les claims JWT.

Dans PowerShell, auprès de `identity-service`, enregistre un nouvel utilisateur de test avec une adresse unique, puis connecte-toi. Utilise des informations fictives et un mot de passe de test différent de tout mot de passe personnel :

```powershell
$testEmail = "coach-test-$([guid]::NewGuid().ToString('N'))@example.test"
$testPassword = "Use-A-New-Local-Test-Password!"
$registerBody = @{
    email = $testEmail
    password = $testPassword
    firstName = "Coach"
    lastName = "Test"
    dateOfBirth = "1990-01-01"
    address = "Adresse de test"
    phoneNumber = "+33100000000"
} | ConvertTo-Json

$newUser = Invoke-RestMethod -Method Post `
    -Uri "http://localhost:8081/users/register" `
    -ContentType "application/json" -Body $registerBody

$loginBody = @{
    email = $testEmail
    password = $testPassword
} | ConvertTo-Json

$login = Invoke-RestMethod -Method Post `
    -Uri "http://localhost:8081/users/login" `
    -ContentType "application/json" -Body $loginBody

$coachToken = $login.accessToken
if ([string]::IsNullOrWhiteSpace($coachToken)) {
    throw "Le service d'identité n'a pas retourné d'access token."
}
```

Vérifie le rôle sans afficher le JWT :

```powershell
$identityHeaders = @{ Authorization = "Bearer $coachToken" }
$me = Invoke-RestMethod -Method Get -Uri "http://localhost:8081/users/me" -Headers $identityHeaders
$me | Select-Object id, role, status
```

Le rôle doit être `ROLE_USER`. Si le compte est encore `ROLE_ADMIN`, ne l’utilise pas pour le test métier; le rôle `ADMIN` sert à accéder à Actuator.

### Configurer le coach pour accepter ce JWT

Inspecte localement les claims publics du JWT pour récupérer l’issuer et l’audience configurés par identity-service. Le décodage ci-dessous **ne vérifie pas la signature**; il sert uniquement à lire les valeurs de configuration. Ne partage jamais le JWT ni sa sortie complète.

```powershell
$payloadPart = $coachToken.Split('.')[1].Replace('-', '+').Replace('_', '/')
switch ($payloadPart.Length % 4) {
    2 { $payloadPart += '==' }
    3 { $payloadPart += '=' }
}
$claims = [Text.Encoding]::UTF8.GetString(
    [Convert]::FromBase64String($payloadPart)
) | ConvertFrom-Json
$claims | Select-Object iss, aud, sub, role
```

Dans le `.env` de `financial-coach-service`, configure `JWT_ISSUER` avec la valeur exacte du claim `iss`, `JWT_AUDIENCE` avec une audience présente dans `aud`, et `JWKS_URI` selon l’endroit où l’application s’exécute :

- Coach lancé dans Docker Compose : `JWKS_URI=http://host.docker.internal:8081/.well-known/jwks.json`.
- Coach lancé directement avec Maven sur Windows : `JWKS_URI=http://localhost:8081/.well-known/jwks.json`.

Vérifie également la clé `GROQ_API_KEY`, `AI_BASE_URL=https://api.groq.com/openai` et `AI_MODEL=openai/gpt-oss-120b` dans `.env`. Ne colle pas de vraie clé dans le terminal, le README ou Git. Puis, depuis le dossier du microservice :

```powershell
docker compose up --build -d
docker compose ps
docker compose logs --tail 100 financial-coach-service
```

Après tout changement de `JWT_ISSUER`, `JWT_AUDIENCE` ou `JWKS_URI`, redémarre le conteneur. Docker Compose lit `.env`; `.\mvnw.cmd spring-boot:run` ne charge pas automatiquement ce fichier. Pour Maven, charge ces variables dans la session PowerShell avant de lancer l’application.

### Faire un premier appel au coach avec le JWT utilisateur

Dans la même session PowerShell où `$coachToken` est défini :

```powershell
$baseUrl = "http://localhost:8082"
$coachHeaders = @{ Authorization = "Bearer $coachToken" }

Invoke-RestMethod -Method Get `
    -Uri "$baseUrl/api/v1/coach/profile" `
    -Headers $coachHeaders
```

Une réponse `200` confirme que le coach a accepté le JWT et que PostgreSQL répond pour cette route. `401` indique généralement que `iss`, `aud`, JWKS, signature ou expiration ne correspondent pas. `403` indique généralement un rôle différent de `USER`; `ROLE_ADMIN` seul n’accorde pas automatiquement le rôle `USER`.

Ensuite, active le consentement IA puis envoie des dépenses fictives :

```powershell
$consentBody = @{ consented = $true; policyVersion = "v1" } | ConvertTo-Json
Invoke-RestMethod -Method Put `
    -Uri "$baseUrl/api/v1/coach/me/consent" `
    -Headers $coachHeaders -ContentType "application/json" -Body $consentBody

$analysisBody = @{
    categorizedExpenses = @{
        ALIMENTATION = 3000.00
        LOISIRS = 800.00
        STREAMING_ABONNEMENTS = 250.00
    }
    userFinancialGoal = "Réduire mes dépenses récurrentes"
} | ConvertTo-Json -Depth 5

$analysis = Invoke-RestMethod -Method Post `
    -Uri "$baseUrl/api/v1/coach/analyze-budget" `
    -Headers $coachHeaders -ContentType "application/json" -Body $analysisBody
$analysis | ConvertTo-Json -Depth 8
```

Vérifie que la réponse contient `verifiedAdvices` et `potentialMonthlySavings`. Une réponse de repli sans conseils signifie que l’appel au modèle n’a pas abouti ou que sa sortie a été rejetée par les vérifications du service; consulte les logs du conteneur sans y ajouter de secrets.

## 3. Profil et calcul du budget

### Lire le profil

```powershell
Invoke-RestMethod -Uri "$baseUrl/api/v1/coach/profile" -Method Get -Headers $headers
```

### Créer ou modifier le profil

Les ratios doivent être des nombres entiers entre 0 et 100 et leur somme doit être exactement 100.

```powershell
$body = @{
    netMonthlyIncome = 12000.00
    needsRatio = 50
    wantsRatio = 30
    savingsRatio = 20
} | ConvertTo-Json

Invoke-RestMethod -Uri "$baseUrl/api/v1/coach/profile" `
    -Method Put -Headers $headers -ContentType "application/json" -Body $body
```

### Calculer une allocation

Sans ratios personnalisés, le service prend ceux du profil; si aucun profil n’existe, il utilise 50/30/20.

```powershell
$body = @{
    netMonthlyIncome = 12000.00
} | ConvertTo-Json

Invoke-RestMethod -Uri "$baseUrl/api/v1/coach/allocate" `
    -Method Post -Headers $headers -ContentType "application/json" -Body $body
```

Pour fournir des ratios à cette requête, renseigner **les trois** champs :

```powershell
$body = @{
    netMonthlyIncome = 12000.00
    customNeedsRatio = 50
    customWantsRatio = 30
    customSavingsRatio = 20
} | ConvertTo-Json

Invoke-RestMethod -Uri "$baseUrl/api/v1/coach/allocate" `
    -Method Post -Headers $headers -ContentType "application/json" -Body $body
```

Le résultat contient les enveloppes Besoins, Envies et Épargne ainsi que les ratios appliqués. Le reliquat d’arrondi est réaffecté aux besoins pour que la somme égale le revenu.

## 4. Consentement et test complet de l’IA Groq

L’analyse IA est refusée (`403`) tant que le consentement de l’utilisateur n’est pas actif. N’envoyez que des données de test ou des dépenses agrégées non identifiantes.

### Lire le statut du consentement

```powershell
Invoke-RestMethod -Uri "$baseUrl/api/v1/coach/me/consent" -Method Get -Headers $headers
```

### Activer le consentement

```powershell
$body = @{
    consented = $true
    policyVersion = "v1"
} | ConvertTo-Json

Invoke-RestMethod -Uri "$baseUrl/api/v1/coach/me/consent" `
    -Method Put -Headers $headers -ContentType "application/json" -Body $body
```

### Analyser des dépenses agrégées

Les catégories reconnues incluent `LOGEMENT`, `ALIMENTATION`, `TRANSPORT`, `LOISIRS`, `RESTAURATION_LIVRAISONS`, `STREAMING_ABONNEMENTS`, `SHOPPING`, `SANTE` et `AUTRE`. Au maximum neuf catégories sont acceptées.

```powershell
$body = @{
    categorizedExpenses = @{
        ALIMENTATION = 3000.00
        LOISIRS = 800.00
        STREAMING_ABONNEMENTS = 250.00
    }
    userFinancialGoal = "Réduire mes dépenses récurrentes"
} | ConvertTo-Json -Depth 5

$result = Invoke-RestMethod -Uri "$baseUrl/api/v1/coach/analyze-budget" `
    -Method Post -Headers $headers -ContentType "application/json" -Body $body
$result | ConvertTo-Json -Depth 8
```

Une réponse avec `verifiedAdvices` et `potentialMonthlySavings` indique que le service a retourné un résultat validé. Si la réponse indique que l’analyse est indisponible avec zéro économie, l’appel fournisseur a échoué ou sa sortie n’a pas satisfait les contrôles du service. Vérifier les logs assainis du conteneur, la configuration `GROQ_API_KEY` dans `.env`, le modèle, l’accès réseau et les erreurs/quota Groq; ne jamais imprimer ni partager la clé.

> Le modèle peut produire un résultat différent d’un appel direct à Groq : le service exige un format structuré puis filtre les catégories et montants. Le service peut donc refuser une sortie du modèle et répondre avec son résultat de repli.

### Révoquer le consentement

```powershell
$body = @{
    consented = $false
    policyVersion = "v1"
} | ConvertTo-Json

Invoke-RestMethod -Uri "$baseUrl/api/v1/coach/me/consent" `
    -Method Put -Headers $headers -ContentType "application/json" -Body $body
```

## 5. Historique des recommandations

```powershell
Invoke-RestMethod -Uri "$baseUrl/api/v1/coach/recommendations?page=0&size=10" `
    -Method Get -Headers $headers | ConvertTo-Json -Depth 8
```

`page` commence à 0; `size` doit être compris entre 1 et 50. La route lit les recommandations persistées pour l’utilisateur. Le fait qu’une analyse IA réponde ne signifie pas automatiquement que son résultat est ajouté à cet historique.

## 6. Détection du salaire

```powershell
Invoke-RestMethod -Uri "$baseUrl/api/v1/coach/salary/detect" `
    -Method Get -Headers $headers | ConvertTo-Json -Depth 8
```

Cette route nécessite que `SERVICES_TRANSACTION_SERVICE_BASE_URL` pointe vers un service de transactions joignable et compatible. Elle transmet le JWT courant à ce service. Si le service externe n’est pas configuré ou ne répond pas, la détection ne peut pas aboutir.

## 7. Coffres

### Créer un coffre

`vaultType` doit être `FIXED_EXPENSES`, `SAVINGS` ou `GOAL`. Les champs `targetAmount` et `autoTransferDate` sont facultatifs.

```powershell
$body = @{
    vaultName = "Vacances"
    vaultType = "GOAL"
    targetAmount = 10000.00
    autoTransferDate = 1
} | ConvertTo-Json

$vault = Invoke-RestMethod -Uri "$baseUrl/api/v1/coach/vaults" `
    -Method Post -Headers $headers -ContentType "application/json" -Body $body
$vault | ConvertTo-Json
$vaultId = $vault.id
```

Garder l’identifiant retourné dans `$vaultId` pour les exemples suivants.

### Lister les coffres

```powershell
Invoke-RestMethod -Uri "$baseUrl/api/v1/coach/vaults" `
    -Method Get -Headers $headers | ConvertTo-Json -Depth 5
```

### Renommer un coffre

```powershell
$body = @{ newVaultName = "Voyage" } | ConvertTo-Json

Invoke-RestMethod -Uri "$baseUrl/api/v1/coach/vaults/$vaultId" `
    -Method Patch -Headers $headers -ContentType "application/json" -Body $body
```

### Supprimer un coffre vide

Cette action est irréversible. Elle échoue si le coffre contient un solde positif.

```powershell
Invoke-RestMethod -Uri "$baseUrl/api/v1/coach/vaults/$vaultId" `
    -Method Delete -Headers $headers
```

Une suppression réussie renvoie normalement HTTP `204` sans corps.

## 8. Verrouillage d’un montant — nécessite un vrai fournisseur SCA

N’effectuez pas ce parcours en production sans avoir validé le fournisseur et les règles financières. En environnement local, le fournisseur SCA est désactivé par défaut; l’intention échouera alors avec une erreur de fournisseur indisponible. Un code à six chiffres arbitraire ne permet pas de confirmer l’opération.

### Créer une intention

```powershell
$body = @{ amount = 100.00 } | ConvertTo-Json

$intent = Invoke-RestMethod -Uri "$baseUrl/api/v1/coach/vaults/$vaultId/lock-intent" `
    -Method Post -Headers $headers -ContentType "application/json" -Body $body
$intent | ConvertTo-Json
$intentId = $intent.intentId
```

Le fournisseur envoie ou délivre le défi selon son propre mécanisme. L’intention expire après cinq minutes.

### Confirmer avec le code SCA reçu

Remplacer la valeur d’exemple par le code réellement délivré et encore valide par le fournisseur SCA. Générer une nouvelle clé d’idempotence pour une nouvelle opération; réutiliser la même clé uniquement pour rejouer exactement la même requête.

```powershell
$scaCode = Read-Host "Code SCA reçu"
$idempotencyKey = [guid]::NewGuid().ToString()
$headersWithIdempotency = @{
    Authorization = "Bearer $token"
    "Idempotency-Key" = $idempotencyKey
}
$body = @{
    intentId = $intentId
    scaCode = $scaCode
} | ConvertTo-Json

Invoke-RestMethod -Uri "$baseUrl/api/v1/coach/vaults/$vaultId/lock-confirm" `
    -Method Post -Headers $headersWithIdempotency `
    -ContentType "application/json" -Body $body | ConvertTo-Json
Remove-Variable scaCode, idempotencyKey, headersWithIdempotency
```

En cas de rejeu exact après une réponse réseau perdue, garder la même clé d’idempotence, le même coffre et la même intention. Une clé existante réutilisée pour une autre opération provoque un conflit `409`. Le fournisseur SCA accepte au maximum cinq tentatives échouées pour l’intention.

## 9. Export et effacement des données

### Exporter les données du compte

```powershell
Invoke-RestMethod -Uri "$baseUrl/api/v1/coach/me/export" `
    -Method Get -Headers $headers | ConvertTo-Json -Depth 12
```

L’export concerne les données gérées par ce microservice; il ne récupère pas les données détenues par les services externes.

### Demander l’effacement

**Attention : cette opération supprime des données et ne doit pas servir de simple test.** Elle est bloquée avec HTTP `409` si un coffre a un solde positif. Certaines écritures ledger/audit peuvent être conservées sous forme pseudonymisée conformément aux règles en vigueur.

```powershell
Invoke-RestMethod -Uri "$baseUrl/api/v1/coach/me/data" `
    -Method Delete -Headers $headers
```

Une demande réussie renvoie normalement HTTP `204` sans corps. Tester cette route uniquement avec un compte et des données de développement dédiés.

## 10. Vérifier Actuator et les métriques

Les endpoints `/actuator/**` sont protégés par le rôle `ADMIN`, et non par `USER`. Pour les appeler, utiliser un JWT d’administration valide :

```powershell
$adminSecureToken = Read-Host "JWT de test (role ADMIN)" -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminSecureToken)
try {
    $adminToken = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
}
$adminHeaders = @{ Authorization = "Bearer $adminToken" }

Invoke-RestMethod -Uri "$baseUrl/actuator/health" -Method Get -Headers $adminHeaders
Invoke-RestMethod -Uri "$baseUrl/actuator/prometheus" -Method Get -Headers $adminHeaders
```

Un JWT `USER` doit être refusé sur Actuator; ne rendez pas ces chemins anonymes pour contourner ce refus.

## 11. Codes d’erreur courants

| HTTP | Signification habituelle |
|---|---|
| `400` | Corps invalide, montant/ratio hors limites ou code SCA mal formé. |
| `401` | JWT manquant, invalide, expiré ou issuer/audience incorrects. |
| `403` | Rôle insuffisant ou consentement IA absent/révoqué. |
| `404` | Ressource introuvable ou appartenant à un autre utilisateur. |
| `409` | Conflit d’idempotence, coffre non supprimable ou effacement bloqué par un solde. |
| `413` | Corps HTTP supérieur à la limite configurée de 65 536 octets. |
| `429` | Limite de débit atteinte ou trop de tentatives SCA. |
| `503` | Fournisseur SCA ou autre service externe indisponible. |

Les erreurs retournent un document de problème (`ProblemDetail`) avec un code. Les messages ne contiennent volontairement pas de secret ni de détails internes.

## 12. Nettoyage de la session PowerShell

Après les tests, retirer les variables contenant le JWT de la session :

```powershell
Remove-Variable token, headers, adminToken, adminHeaders, secureToken, adminSecureToken `
    -ErrorAction SilentlyContinue
```

Pour les étapes détaillées d’architecture et de sécurité, voir `README4.md`; l’état et les validations par étape sont dans `README3.md`.
