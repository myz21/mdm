# Apple MDM Komut Sistemi — Araştırma Ödevi

## Bağlam

Apple MDM protokolünde sunucu, cihaza doğrudan komut gönderemez. Bunun yerine Apple Push Notification Service (APNs) üzerinden cihaza bir **wake-up bildirimi** gönderir; cihaz bu bildirimi aldığında sunucuya bağlanarak bekleyen komutunu çeker.

Basitleştirilmiş akış:
```
1. Admin "Cihazı Kilitle" butonuna tıklar
2. Sunucu komutu kaydeder
3. Sunucu APNs üzerinden cihaza "uyan, komutun var" der
4. Cihaz uyanır, sunucuya bağlanır
5. Sunucu komutu verir
6. Cihaz komutu çalıştırır ve sonucu bildirir
```

Bu akışı göz önünde bulundurarak aşağıdaki soruları cevaplayın. Cevaplarınızı hem kendi araştırmanızla hem de projede ilgili dosyaları inceleyerek destekleyin.

---

## Sorular

### a) Veri Yapısı Seçimi

Bir cihaza sırasıyla 3 komut gönderildiğini düşünün: önce WiFi profili yükle, sonra uygulama kur, sonra cihazı kilitle.

- Bu komutların sırasını korumak için hangi veri yapısını kullanırdınız? (Queue, Stack, veya başka bir yapı?) Sebebini açıklayın.
- Bu veri yapısını programın belleğinde mi (in-memory) yoksa harici bir yerde mi tutardınız? Sunucu yeniden başlatılırsa ne olur?
- Projede `RedisAppleCommandQueueServiceImpl` dosyasını inceleyin — komutları saklamak için nasıl bir yapı tercih edilmiş?

### b) Komut Durumları

Bir komut oluşturulduğu andan cihazda işlenene kadar birkaç aşamadan geçer.

- PENDING, EXECUTING, COMPLETED, FAILED gibi durumlar ne anlama gelir? Her birini kendi cümlelerinizle açıklayın.
- Projede bir cihaza komutlar neden tek tek sırayla veriliyor, hepsi bir seferde verilmiyor? Bu tasarım kararının sebebi ne olabilir?
- Cihaz komutu aldıktan sonra uzun süre cevap vermezse sisteminiz ne yapmalı? Projede bu durumu ele alan mekanizmayı bulun.

### c) Birden Fazla Sunucu (Multi-Instance)

Sunucu yükü artınca aynı uygulamadan 2 kopya (instance) çalıştırılıyor. Her ikisi de aynı Redis'e bağlı.

- Bir cihazın bekleyen komutu varken, iki instance aynı anda bu komutu okuyup işlemeye çalışırsa ne gibi bir sorun oluşur?
- Bu "yarış durumunu" (race condition) önlemek için ne tür bir mekanizma kullanırdınız? (İpucu: "lock" kavramını araştırın.)
- Projede `LOCK_KEY_PREFIX` sabitini bulun — nasıl bir kilitleme uygulanmış?

### d) Sunucu Çökerse Ne Olur?

- Komut kaydedildikten sonra sunucu çökerse komut kaybolur mu? Neden veya neden kaybolmaz?
- Projede sunucu başlangıcında çalışan bir "kurtarma" mekanizması var mı? (`loadPendingCommandsOnStartup` veya benzer bir metod arayın.)
- Komutlar hem Redis'e hem veritabanına yazılıyor — bunun sebebi ne olabilir?

---

## İncelenmesi Gereken Dosyalar

| Dosya | Konum | Ne Bulacaksınız |
|-------|-------|-----------------|
| `RedisAppleCommandQueueServiceImpl.java` | `services/apple/command/` | Komut saklama, kilitleme, timeout temizleme |
| `AppleCheckinServiceImpl.java` | `services/enrollment/` | Cihazın sunucuya bağlanma akışı |
| `AppleCommand.java` | `domains/` | Komut entity'si — hangi alanlar var, status nasıl tutuluyor |
| `AgentPresenceService.java` | `services/agent/` | Cihazın online/offline durumu nasıl takip ediliyor |
