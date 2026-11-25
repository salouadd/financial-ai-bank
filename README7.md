# README7 — Démarrer Identity Service et Financial Coach

Ce guide décrit le parcours local complet pour obtenir un JWT de test auprès d’**Identity Service**, configurer **Financial Coach** pour le reconnaître, puis appeler ses routes métier. Les exemples sont pour Windows PowerShell et supposent Identity Service sur le port `8081` et Financial Coach sur le port `8082`.

> **Utilisez uniquement des comptes et des données fictifs de développement.** Ne mettez jamais de vrais mots de passe, JWT, codes SCA ou clés API dans un README, Git ou un message. La clé Groq précédemment partagée dans une conversation doit être révoquée et remplacée.

## 0. Comprendre les rôles nécessaires

- Les routes métier de Financial Coach (`/api/v1/coach/**`) exigent un JWT dont le claim `role` autorise `ROLE_USER`.
- Les routes Actuator (`/actuator/**`) exigent un JWT `ROLE_ADMIN`.
- Un compte `ROLE_ADMIN` seul n’est pas automatiquement un `ROLE_USER`. Utilisez un compte `ROLE_USER` distinct pour les tests métier.
- Le coach utilise le claim `sub` du JWT comme identifiant UUID propriétaire. Il vérifie aussi la signature avec JWKS, `iss` (issuer), `aud` (audience) et l’expiration.
- Le statut KYC et les rôles sont deux notions différentes. Suivez les règles KYC d’Identity Service, mais pour l’accès au coach vérifiez surtout le rôle et les claims effectivement émis dans le JWT.

Vous aurez besoin de deux fenêtres PowerShell :

1. Une dans le dossier du dépôt `identity-service`.
2. Une dans le dossier du dépôt `financial-coach-service`.

## 1. Démarrer et vérifier Identity Service

Dans la fenêtre Identity Service, démarrer le service selon son README/procédure locale, puis vérifier sa santé :

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8081/actuator/health"
Invoke-RestMethod -Method Get -Uri "http://localhost:8081/actuator/health/readiness"
```

Les deux réponses doivent indiquer `UP`. Vérifier aussi que le document de clés publiques JWKS est accessible :

```powershell
$jwks = Invoke-RestMethod -Method Get -Uri "http://localhost:8081/.well-known/jwks.json"
$jwks.keys | Select-Object kid, kty, alg, use
```

Ne partagez pas de JWT ou de données personnelles pour diagnostiquer ces requêtes.

## 2. Créer un compte utilisateur de test

Dans la fenêtre Identity Service, utiliser une adresse unique et des informations fictives. Ne réutilisez pas le mot de passe exposé précédemment dans le terminal partagé.

```powershell
$testEmail = "coach-test-$([guid]::NewGuid().ToString('N'))@example.test"
$testPassword = Read-Host "Mot de passe temporaire du compte de test"

$registerBody = @{
    email = $testEmail
    password = $testPassword
    firstName = "Coach"
    lastName = "Test"
    dateOfBirth = "1990-01-01"
    address = "Adresse de test"
    phoneNumber = "+33100000000"
} | ConvertTo-Json

$newUser = Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8081/users/register" `
    -ContentType "application/json" `
    -Body $registerBody

$newUser | Select-Object id, email, role, status
```

Conservez l’identifiant du compte de test, mais ne publiez pas ses données. Selon la configuration d’Identity Service, une étape de KYC ou d’activation peut être requise avant d’obtenir l’état voulu.

Pour le test d’accès au coach, le KYC n’est pas une étape technique obligatoire si Identity Service permet déjà la connexion et émet un JWT `ROLE_USER`. Le coach autorise les routes métier à partir du JWT; il ne consulte pas directement le statut KYC dans Identity Service.

### Si le KYC est obligatoire dans votre configuration d’identité

Effectuez les étapes suivantes uniquement dans un environnement local et avec un fichier **fictif de test**, jamais avec le passeport, la carte d’identité ou un document réel d’une personne. Utilisez un compte Admin de développement séparé pour la vérification.

1. Connectez l’utilisateur de test et téléchargez le document de test en suivant le contrat KYC décrit dans le README d’Identity Service.
2. Connectez le compte Admin de développement et vérifiez le document avec la route d’administration documentée par Identity Service.
3. Reconnectez l’utilisateur et vérifiez `/users/me` pour constater l’état après KYC.

Le parcours KYC n’ajoute pas à lui seul un rôle `USER` au JWT. Si le compte retourne `ROLE_ADMIN`, utilisez un utilisateur de test distinct ou corrigez les rôles via la procédure administrative officielle d’Identity Service; ne modifiez pas un JWT à la main.

## 3. Se connecter avec le compte `ROLE_USER`

Toujours dans la fenêtre Identity Service :

```powershell
$loginBody = @{
    email = $testEmail
    password = $testPassword
} | ConvertTo-Json

$login = Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8081/users/login" `
    -ContentType "application/json" `
    -Body $loginBody

$userToken = $login.accessToken
if ([string]::IsNullOrWhiteSpace($userToken)) {
    throw "Identity Service n'a pas retourné d'access token."
}

$identityHeaders = @{ Authorization = "Bearer $userToken" }
$me = Invoke-RestMethod `
    -Method Get `
    -Uri "http://localhost:8081/users/me" `
    -Headers $identityHeaders

$me | Select-Object id, role, status
```

Avant de continuer, confirmez que `role` correspond à `ROLE_USER`. Si le login échoue parce que le compte n’est pas activé ou vérifié, terminez d’abord la procédure locale d’Identity Service. Ne partagez pas la valeur de `$userToken`.

## 4. Lire les claims JWT nécessaires sans afficher le jeton

Le coach doit être configuré avec les valeurs réellement émises par **votre** Identity Service. Le code ci-dessous décode localement la partie publique (payload) du JWT pour inspecter les claims; il **ne vérifie pas** la signature et ne remplace jamais la validation cryptographique du coach.

```powershell
$payloadPart = $userToken.Split('.')[1].Replace('-', '+').Replace('_', '/')
switch ($payloadPart.Length % 4) {
    2 { $payloadPart += '==' }
    3 { $payloadPart += '=' }
}
$claims = [Text.Encoding]::UTF8.GetString(
    [Convert]::FromBase64String($payloadPart)
) | ConvertFrom-Json

$claims | Select-Object iss, aud, sub, role
```

Notez localement :

- `iss` : valeur exacte à mettre dans `JWT_ISSUER`;
- `aud` : la valeur audience destinée au coach à mettre dans `JWT_AUDIENCE`;
- `sub` : doit être un UUID stable;
- `role` : doit autoriser `ROLE_USER`.

Si une de ces valeurs manque ou si l’audience ne désigne pas le coach, configurez le contrat côté Identity Service et réémettez un jeton. Ne modifiez jamais le token côté client.

## 5. Configurer Financial Coach

Dans la fenêtre Financial Coach, depuis le dossier `financial-coach-service`, vérifier que `.env` existe :

```powershell
if (-not (Test-Path .env)) {
    Copy-Item .env.example .env
}
notepad .env
```

Configurer les variables locales selon votre environnement. Exemple de structure — remplacez les valeurs d’identité par les valeurs exactes relevées à l’étape précédente :

```dotenv
JWT_ISSUER=https://valeur-iss-de-votre-identity-service
JWT_AUDIENCE=valeur-aud-configuree-pour-financial-coach
JWKS_URI=http://host.docker.internal:8081/.well-known/jwks.json

GROQ_API_KEY=votre_cle_groq_developpement
AI_BASE_URL=https://api.groq.com/openai
AI_MODEL=openai/gpt-oss-120b
```

`host.docker.internal` permet au conteneur Financial Coach d’accéder à Identity Service lancé sur l’hôte Windows. Si le coach est lancé directement avec Maven sous Windows, utilisez plutôt :

```dotenv
JWKS_URI=http://localhost:8081/.well-known/jwks.json
```

Ne mettez aucune vraie clé dans `.env.example` ou dans Git. `.env` est un fichier local ignoré par Git. Une clé factice ou absente permet éventuellement au contexte local de démarrer, mais ne peut pas appeler réellement Groq.

### Variables PostgreSQL

Pour le démarrage Docker Compose, `.env` doit également fournir les paramètres PostgreSQL requis par `docker-compose.yml`, avec les mêmes identifiants entre le conteneur PostgreSQL et l’application. Utilisez des identifiants de développement locaux, pas ceux d’une base de production :

```dotenv
POSTGRES_DB=coach_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=un_mot_de_passe_local_de_test
```

## 6. Démarrer Financial Coach avec Docker Compose

Depuis la racine du dépôt Financial Coach :

```powershell
docker compose up --build -d
docker compose ps
docker compose logs --tail 150 financial-coach-service
```

Attendez que PostgreSQL soit sain et que l’application ait terminé ses migrations Flyway. Le coach doit écouter sur le port `8082`.

```powershell
docker compose ps
docker compose logs --tail 100 financial-coach-service
```

Le service Actuator du coach exige `ROLE_ADMIN`; le JWT utilisateur de ce guide ne peut donc pas servir à appeler `/actuator/health`. Pour le parcours métier, vérifiez plutôt `GET /api/v1/coach/profile` à l’étape suivante.

> Docker Compose charge `.env` automatiquement. `.\mvnw.cmd spring-boot:run` ne charge pas automatiquement `.env`; pour ce mode, exportez les variables nécessaires dans la session PowerShell avant de lancer Maven.

## 7. Appeler Financial Coach avec le JWT `ROLE_USER`

Dans la fenêtre où `$userToken` est encore défini :

```powershell
$coachBaseUrl = "http://localhost:8082"
$coachHeaders = @{ Authorization = "Bearer $userToken" }
```

### 7.1 Vérifier l’authentification et l’accès à PostgreSQL

```powershell
$profile = Invoke-RestMethod `
    -Method Get `
    -Uri "$coachBaseUrl/api/v1/coach/profile" `
    -Headers $coachHeaders

$profile | ConvertTo-Json -Depth 5
```

Une réponse `200` confirme que le JWT est accepté sur cette route et que la lecture du profil fonctionne.

### 7.2 Enregistrer un profil budgétaire (optionnel)

Les ratios sont des entiers dont la somme doit être `100`.

```powershell
$profileBody = @{
    netMonthlyIncome = 12000.00
    needsRatio = 50
    wantsRatio = 30
    savingsRatio = 20
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Put `
    -Uri "$coachBaseUrl/api/v1/coach/profile" `
    -Headers $coachHeaders `
    -ContentType "application/json" `
    -Body $profileBody
```

### 7.3 Calculer la répartition

```powershell
$allocationBody = @{
    netMonthlyIncome = 12000.00
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri "$coachBaseUrl/api/v1/coach/allocate" `
    -Headers $coachHeaders `
    -ContentType "application/json" `
    -Body $allocationBody
```

### 7.4 Tester l’analyse Groq

Le consentement doit être actif avant l’appel IA :

```powershell
$consentBody = @{
    consented = $true
    policyVersion = "v1"
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Put `
    -Uri "$coachBaseUrl/api/v1/coach/me/consent" `
    -Headers $coachHeaders `
    -ContentType "application/json" `
    -Body $consentBody
```

Envoyez des montants de test agrégés, sans nom, compte bancaire ni transaction individuelle :

```powershell
$analysisBody = @{
    categorizedExpenses = @{
        ALIMENTATION = 3000.00
        LOISIRS = 800.00
        STREAMING_ABONNEMENTS = 250.00
    }
    userFinancialGoal = "Réduire mes dépenses récurrentes"
} | ConvertTo-Json -Depth 5

$analysis = Invoke-RestMethod `
    -Method Post `
    -Uri "$coachBaseUrl/api/v1/coach/analyze-budget" `
    -Headers $coachHeaders `
    -ContentType "application/json" `
    -Body $analysisBody

$analysis | ConvertTo-Json -Depth 8
```

Un résultat validé contient `verifiedAdvices` et `potentialMonthlySavings`. Le service peut retourner un résultat de repli sans conseils ni économies si Groq est indisponible ou si la sortie du modèle ne passe pas la validation métier. Cela ne signifie pas nécessairement que l’authentification est en panne.

## 8. Résultats attendus et diagnostic rapide

| Résultat | Interprétation et vérification |
|---|---|
| `200` sur `/api/v1/coach/profile` | JWT accepté pour le coach et lecture du profil effectuée. |
| `401 Unauthorized` | Vérifier le JWT courant, son expiration et sa signature; comparer `iss`/`aud` aux valeurs configurées; vérifier `JWKS_URI` depuis le conteneur et les logs. |
| `403 Forbidden` sur une route métier | Vérifier le claim `role`: le coach attend `ROLE_USER`. Un compte Admin seul n’est pas un substitut. Pour l’analyse IA, vérifier aussi le consentement actif. |
| `503` au démarrage ou pendant le JWT | Vérifier l’accès de Financial Coach à `http://host.docker.internal:8081/.well-known/jwks.json`, le port et les logs Identity Service. |
| `200` avec résultat IA de repli | L’authentification a probablement fonctionné, mais l’appel Groq a échoué ou sa sortie a été rejetée. Vérifier la clé Groq localement, le modèle, le quota réseau/fournisseur et les logs assainis. Ne jamais afficher la clé. |
| PostgreSQL indisponible ou migrations en erreur | Vérifier `docker compose ps`, les identifiants DB de `.env` et les logs du service. |
| `403` après révocation du consentement | Comportement attendu : réactiver le consentement pour refaire un test IA. |

Pour examiner les logs du coach :

```powershell
docker compose logs --tail 200 financial-coach-service
docker compose logs --tail 100 db
```

Ne publiez pas les logs complets sans vérifier qu’ils ne contiennent ni données personnelles ni secrets.

## 9. Arrêter et nettoyer les variables locales

Arrêter les conteneurs sans supprimer le volume de données :

```powershell
docker compose down
```

Effacer les variables qui contiennent le JWT et les identifiants de test des sessions PowerShell :

```powershell
Remove-Variable userToken, coachHeaders, identityHeaders, login, loginBody, registerBody, `
    testPassword, claims, payloadPart, adminToken -ErrorAction SilentlyContinue
```

Ne supprimez pas un volume PostgreSQL contenant des données que vous souhaitez conserver. Pour la liste des routes et leurs exemples détaillés, voir `README5.md`; pour l’architecture et l’état des étapes, voir `README4.md` et `README3.md`.
