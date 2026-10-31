# Plan de autoactualización por APK en Android TV

Historical proposal. Implemented behavior and current limits: [implementation guide](../auto-update.md).

Estado: propuesta, sin implementación. Fecha: 31 de octubre de 2026.

## Objetivo y alcance

Al abrir el catálogo, consultar una URL HTTPS controlada por el propietario. Si existe una versión posterior, descargar y verificar su APK, y ofrecer la instalación mediante la interfaz de Android. La app seguirá funcionando con la versión instalada si el usuario pospone, cancela o no tiene red.

La instalación inicial podrá hacerse mediante Wireless debugging o transferencia manual. Las actualizaciones posteriores no necesitarán ADB mientras funcionen este módulo, su alojamiento y el instalador del dispositivo. Una avería del módulo o una firma incompatible puede requerir otra intervención manual.

`REQUEST_INSTALL_PACKAGES` permite solicitar la instalación, pero no concede instalación silenciosa. La primera actualización requerirá autorizar a South Park TV como origen de apps desconocidas. Después seguirá existiendo confirmación del sistema; "pulsar una vez" es una meta para esa confirmación, no una garantía del número total de pantallas o pulsaciones. Fabricantes y políticas del dispositivo pueden añadir pasos o impedir la instalación.

El módulo distribuye únicamente el APK de esta app. No modifica reproducción, streams, publicidad, autenticación, DRM ni restricciones regionales.

## Punto de partida revisado

- `app/build.gradle`: `applicationId` y namespace `com.unofficial.southparktv`, `versionCode 6`, `versionName '0.5.1'`, `minSdk 26`, `targetSdk 35`, compile SDK 36.1 y Java 17. No hay configuración explícita de firma release.
- `README.md` documenta instalación del APK debug. No se ha inspeccionado el certificado de la app instalada en ninguna TV Box.
- `AndroidManifest.xml` declara Internet y estado de red, prohíbe tráfico HTTP y exige Leanback. Todavía no declara permiso de instalación ni componentes de actualización.
- `MainActivity.onCreate()` construye el catálogo y llama a `loadSeasons()`. Sus solicitudes tienen ejecutores y guardas de generación. La comprobación nueva debe ser independiente para no bloquear esa cola ni alterar sus guardas.
- La presentación seguirá `docs/design-system.md`, con Java Views, recursos y `TvUi`. `VALIDATION.md` y `docs/site-protocol.md` delimitan la evidencia actual; no prueban este módulo ni compatibilidad de instaladores físicos.
- Aunque `AGENTS.md` contiene una nota histórica de ausencia de Git, este checkout sí tiene repositorio y remoto. Esta entrega incorpora únicamente este documento.

## 1. Versionado y continuidad de firma

`versionCode` será un entero creciente para cada APK publicado. La primera entrega posterior a la base revisada usará al menos `7`, comprobando antes que no exista ya una publicación con ese número. Comparar con el código realmente instalado mediante `PackageManager`, usando API compatible con Android 8. No decidir por el nombre de archivo, el tag o una comparación textual de versiones.

`versionName` será la etiqueta legible, por ejemplo `0.6.0`, y el tag de release será `v0.6.0`. El nombre no determina si hay actualización. Solo aceptar un código estrictamente mayor; para corregir una release defectuosa, publicar otra con código superior, sin downgrade automático.

Mantener el mismo `applicationId` y certificado de firma. Establecer una clave release duradera, con copia segura y contraseñas fuera del repositorio, del APK y de logs. No rotar claves en la primera versión del módulo. Android verificará la firma al instalar; el módulo también la contrastará antes de ofrecer el APK.

Antes del despliegue inicial hay que identificar la firma instalada. Si es una build debug y la release usa otra clave, Android rechazará actualizarla sobre la existente. Planificar una migración manual explícita; desinstalar puede borrar historial y preferencias porque la app no ofrece exportación y tiene backup desactivado. No desinstalar automáticamente ni prometer conservación de datos en esa migración. Instalar desde el principio una build con el módulo y la clave definitiva evita repetir este cambio.

## 2. Alojamiento recomendado

Usar GitHub Releases en un repositorio público controlado por el propietario, que puede ser exclusivo para distribuir binarios. Permite descargar assets sin credenciales embebidas ni Play Store. GitHub documenta que Releases no tiene límite de ancho de banda; comprobar sus condiciones y límites de assets al publicar. No se contratará ningún servicio ni se publicarán assets como parte de este plan.

Proponer un asset `update.json` en cada release estable y un APK firmado con nombre específico de versión. La URL de consulta será `https://github.com/OWNER/REPO/releases/latest/download/update.json`. `OWNER/REPO` es un marcador pendiente de elegir, no un endpoint operativo. Evitar la API de GitHub para la comprobación inicial reduce dependencia de sus cuotas y de tokens.

Contrato propuesto para los metadatos:

| Campo | Regla |
| --- | --- |
| `schemaVersion` | `1`; rechazar esquemas desconocidos. |
| `applicationId` | Exactamente `com.unofficial.southparktv`. |
| `versionCode` | Entero positivo dentro del rango admitido por Android. |
| `versionName` | Texto breve para mostrar, nunca criterio de orden. |
| `minSdk` | API mínima del APK. Debe ser compatible con el dispositivo. |
| `apkUrl` | HTTPS a un asset de release con tag concreto del repositorio autorizado. |
| `sha256` | 64 dígitos hexadecimales, calculados sobre el APK final firmado. |
| `sizeBytes` | Tamaño positivo exacto para comprobar integridad y espacio. |

Publicar un APK completo instalable, no un AAB ni un conjunto de splits. Confirmar compatibilidad de las ABI incluidas con las TV Box previstas antes de prometer un APK universal. Las notas serán texto sin HTML, opcionales y acotadas.

Preparar una release como borrador, subir APK y JSON y comprobar sus valores antes de hacerla pública y marcarla como latest estable. El JSON debe apuntar al APK del tag concreto, no a otro enlace `latest`; así un cambio de release durante una descarga no mezcla metadatos y binario. No reemplazar assets ya publicados. Mantener estable el canal público sin promoción automática de prereleases.

Un servidor estático HTTPS propio sería una alternativa con el mismo contrato. Para la primera versión, fijar URL y repositorio en la build; el propietario los controla desde el alojamiento. No aceptar URLs arbitrarias por intents o contenido del proveedor. Una pantalla de configuración editable queda fuera del primer alcance.

## 3. Comprobación al arranque e instalación

1. Mostrar el catálogo inmediatamente y lanzar la consulta en segundo plano al entrar en `MainActivity`. No usar `BOOT_COMPLETED`, servicios permanentes ni esperar a la red antes de cargar temporadas. Consultar una vez por arranque de proceso; `onResume()` y recreaciones no deben duplicarla.
2. Ejecutar en un componente propio de actualización con su ejecutor, timeouts y cancelación. `MainActivity` solo coordina estado y presentación. Descartar callbacks de actividades destruidas y resultados con generación antigua. No reutilizar `SiteClient` ni relajar sus gates de URL oficiales para admitir GitHub.
3. Descargar un JSON acotado, propuesta inicial de 64 KiB, con timeout de conexión de 5 s y lectura de 10 s. Un fallo no bloqueará el catálogo. Validar esquema, campos, origen y compatibilidad antes de comparar códigos. Con versión igual o inferior, terminar sin aviso.
4. Si hay actualización, mostrar una acción discreta en el catálogo con versión y tamaño. No robar foco, abrir diálogos desde segundo plano ni interrumpir reproducción. Si el usuario entra al player mientras llega el resultado, posponer la presentación hasta volver al catálogo.
5. Al elegir **Descargar actualización**, mostrar progreso y **Cancelar**. Descargar a almacenamiento privado en un archivo temporal, con límite inicial de 200 MiB, timeout de inactividad de 30 s y límite total de 10 minutos. Comprobar espacio para APK y margen de instalación; Android puede necesitar espacio adicional. No pedir permisos de almacenamiento compartido.
6. Verificar tamaño y SHA-256, y leer del APK su paquete, versión, SDK mínimo y certificado. Compararlos con el JSON, el dispositivo y la firma instalada. Rechazar APK no firmado, corrupto, incompatible o de otro paquete. La huella de confianza procede de la instalación válida, no de un campo remoto. Renombrar el temporal como listo solo tras verificarlo.
7. Mostrar **Instalar** y **Más tarde**. Antes de instalar, consultar `canRequestPackageInstalls()`. Si falta autorización, explicar el motivo y abrir `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` para el paquete propio mediante una acción explícita. Al volver, reconsultar el permiso, conservar el APK verificado y pedir que se elija **Instalar**; no asumir concesión ni abrir el instalador en bucle.
8. Usar `PackageInstaller` del framework con una sesión para el APK completo. Escribir el APK privado, sincronizarlo y confirmar la sesión. Gestionar `STATUS_PENDING_USER_ACTION` abriendo la confirmación del sistema cuando la app esté visible. Planificar un receptor explícito, no exportado, y el `IntentSender` con las flags exigidas por cada API. No solicitar instalación silenciosa. Esta vía evita un `FileProvider` y una dependencia adicional.
9. Si Android cancela o rechaza la sesión, volver al catálogo con una explicación y reintento explícito. Abandonar sesiones propias que no se completen. Nunca anunciar éxito solo por haber abierto el instalador.
10. La actualización puede terminar el proceso. Guardar únicamente el estado mínimo necesario y, en el siguiente arranque, contrastar la versión instalada con la pendiente. Limpiar APK y sesiones obsoletas; no depender de recibir un callback en el proceso anterior ni prometer reapertura automática.

La primera versión no mantendrá descargas al salir de la app o morir el proceso. Cancelará la transferencia y eliminará parciales al recuperar el estado; el usuario podrá volver a iniciarla. Los APK completos pendientes podrán conservarse hasta 7 días en almacenamiento privado, revalidándolos antes de instalar y limpiándolos después. No borrar un archivo que esté siendo transferido a una sesión activa.

## 4. Red y recuperación de errores

| Situación | Comportamiento propuesto |
| --- | --- |
| Sin red, DNS o timeout de consulta | Mantener versión instalada y catálogo disponible según su propia conectividad. Estado discreto y **Reintentar** accesible con D-pad; sin diálogo bloqueante ni bucle automático. |
| HTTP 404/5xx, rate limit o portal cautivo | Informar de que no se pudo comprobar. Rechazar HTML donde se esperaba JSON/APK. Respetar `Retry-After`; sin martillear el servidor. |
| TLS inválido o redirección no permitida | Detener; nunca desactivar validación de certificados ni recurrir a HTTP. |
| JSON inválido, campos incoherentes o SDK incompatible | No ofrecer instalación. Explicación breve y conservar la versión instalada. |
| Descarga cortada o cancelada | Eliminar parcial, permitir reiniciar y conservar foco. Sin reanudar bytes sin validación adicional en esta primera versión. |
| Espacio insuficiente | Explicar cuánto se necesita y permitir reintentar tras liberar espacio. No borrar datos de usuario. |
| Tamaño, hash, paquete o firma incorrectos | Eliminar el APK y mostrar error de verificación. Nunca abrir el instalador. |
| Permiso denegado o ajustes/instalador ausentes | Continuar usando la app. Explicar que la TV Box no permite este flujo; ofrecer guía de instalación manual confiable. |
| Instalación cancelada, incompatible o fallida | Mostrar el estado real del sistema sin afirmar actualización; permitir reintento explícito. |

Separar estos estados de los errores del catálogo. Si se pulsa **Más tarde**, recordar el código pospuesto y no insistir en ese arranque; mantener la acción de actualización accesible. Un código nuevo puede volver a anunciarse en otro arranque.

## UX con mando de TV

Usar recursos `strings.xml`, dp/sp y tokens existentes. Mantener el idioma inglés actual de la app salvo que se autorice localizarla. Estilizar acciones y diálogos mediante `TvUi`, con estados de foco visibles, márgenes seguros y texto legible a distancia. La red y la instalación no pertenecen a `TvUi` ni a `EpisodeCard`.

Todas las acciones, incluyendo Reintentar, Cancelar y Más tarde, deben alcanzarse con flechas y OK. Back cerrará el aviso o cancelará la descarga con estado claro, y recuperará el control que abrió el flujo. No usar temporizadores de instalación, countdowns ni activación por mero foco. Las actualizaciones de progreso no moverán el foco ni producirán anuncios accesibles por cada porcentaje.

Los ajustes y el instalador son interfaces del sistema. Probar su navegación con mando en cada modelo previsto; la app no puede imponerles su diseño ni certificar su accesibilidad. Si no existe ruta D-pad viable, documentar ese dispositivo como no compatible con el flujo propuesto.

## Seguridad y límites de sideload

Exigir HTTPS, validación TLS normal y una lista explícita de hosts y rutas para el repositorio elegido. GitHub redirige descargas a infraestructura de assets; identificar esos destinos al implementar, permitir solo los necesarios y limitar la cadena a cinco saltos HTTPS. No permitir credenciales en URL, hosts arbitrarios ni degradación a HTTP. No registrar tokens, URLs firmadas temporales ni binarios en logs.

SHA-256 detecta corrupción, pero un atacante que controle el alojamiento podría cambiar APK y checksum a la vez. La continuidad de la firma Android es la barrera de autenticidad del binario; mantener la clave privada separada del hosting. Esta primera propuesta no incorpora firma independiente del JSON: un compromiso del canal todavía podría ocultar versiones o alterar notas. Si se añade después, fijar la clave pública en la app y diseñar su rotación explícitamente.

El permiso de instalación es especial por origen desde Android 8, no un diálogo de permiso runtime convencional. Que ADB haya instalado la app no significa que esta ya pueda instalar actualizaciones. No se necesitan root, ADB permanente, accesibilidad automatizada ni desactivar protecciones. Las políticas de empresa, controles del fabricante y protecciones del sistema pueden bloquear sideload. Revisar también los requisitos de verificación de desarrolladores vigentes para la región, fecha y dispositivo antes de distribuir; estar fuera de Play Store no elimina las reglas de Android.

Los TV Box con Android móvil o sin Leanback no quedan incluidos automáticamente: el manifest actual exige Android TV. No ampliar compatibilidad ni cambiar SDK como parte del módulo sin una evaluación separada.

## 5. Cambios futuros previstos en build.gradle

Esta sección describe trabajo futuro; esta entrega no edita Gradle ni genera un APK.

- Incrementar `defaultConfig.versionCode` y actualizar `versionName` para cada release. Mantener `applicationId`, namespace, SDK y Java actuales salvo necesidad independiente demostrada.
- Definir `signingConfigs` y enlazar la firma a `buildTypes.release`. Obtener keystore, alias y contraseñas desde secretos del entorno de publicación o un archivo local excluido; fallar la build de publicación si faltan, sin fallback a debug.
- Fijar la URL del canal en un recurso de configuración de release. Si se elige `BuildConfig` como mecanismo, habilitar expresamente su generación según AGP 9.1; no es necesaria una librería de updater ni cambiar Media3/IMA.
- Publicar el resultado firmado de `assembleRelease`, no el `app-debug.apk` actual. Obtener hash, tamaño y metadatos del artefacto final y comprobar su firma con `apksigner` antes de alojarlo. La generación/publicación del JSON puede ser una tarea posterior o un proceso manual verificable.

Fuera de Gradle, la implementación futura necesitará declarar `REQUEST_INSTALL_PACKAGES`, añadir el receptor de estado de instalación y sus recursos/UI, y crear el componente de actualización con un punto de entrada en `MainActivity`. No se propone un subproyecto Gradle ni una nueva dependencia. Mantener el tráfico HTTP prohibido y los permisos de publicidad eliminados. No tocar `PlayerActivity`, `Catalog`, `SiteClient`, `WatchHistory` ni lógica del proveedor.

## Secuencia y aceptación para una implementación posterior

Primero resolver repositorio público, URL, clave release y migración desde la firma instalada. Después implementar lectura/validación de metadatos, descarga/verificación y finalmente permiso/instalador y UX. No publicar ni instalar nada hasta autorizar esa implementación.

Las pruebas deberán usar resultados esperados independientes: instalado `6` frente a remoto `6` o `5` no actualiza, frente a `7` sí; nombres como `0.10.0` no deciden el orden. Incluir fixtures de JSON inválido, hash incorrecto, firma distinta, otro paquete y SDK incompatible. Cubrir falta de red, timeout, cancelación, falta de espacio, muerte del proceso, callbacks obsoletos y entradas repetidas al catálogo.

En `emulator-5554`, probar dos APK de prueba con el mismo paquete y firma, códigos consecutivos y conservación del historial; verificar la versión realmente instalada. Probar denegación/concesión del permiso, cancelación del instalador y recorrido completo D-pad/Back con recuperación de foco. Ejecutar build, unit tests, lint, tokens y regresión de catálogo/player según `AGENTS.md`, incluida evidencia de audio. Los fixtures de actualización usarán un canal de prueba separado del público.

No conectar ni instalar en una TV física sin nueva autorización y dirección actual de Wireless debugging. El éxito del emulador no prueba instalador, almacenamiento, permisos, accesibilidad o compatibilidad de una TV Box real. En esta PR documental no se ejecutan builds, instalaciones ni pruebas de este feature.

## Referencias oficiales

Consultadas el 31 de octubre de 2026. Las decisiones de UX, límites de tamaño y timeouts de este documento son propuestas del proyecto.

- [Versionado de Android](https://developer.android.com/studio/publish/versioning): `versionCode` y `versionName`.
- [Firma de apps](https://developer.android.com/studio/publish/app-signing): identidad y continuidad de actualizaciones.
- [Distribución e instalación fuera de tienda](https://developer.android.com/studio/publish): autorización de orígenes y `canRequestPackageInstalls()`.
- [PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller): sesiones, estados y confirmación del sistema.
- [GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases) y [enlaces de assets latest](https://docs.github.com/en/repositories/releasing-projects-on-github/linking-to-releases).
