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

## Sorular ve Cevaplar

### a) Veri Yapısı Seçimi

**- Bu komutların sırasını korumak için hangi veri yapısını kullanırdınız? (Queue, Stack, veya başka bir yapı?) Sebebini açıklayın.**

_Cevabınız:_


**- Bu veri yapısını programın belleğinde mi (in-memory) yoksa harici bir yerde mi tutardınız? Sunucu yeniden başlatılırsa ne olur?**

_Cevabınız:_


**- Projede `RedisAppleCommandQueueServiceImpl` dosyasını inceleyin — komutları saklamak için nasıl bir yapı tercih edilmiş?**

_Cevabınız:_



---

### b) Komut Durumları

**- PENDING, EXECUTING, COMPLETED, FAILED gibi durumlar ne anlama gelir? Her birini kendi cümlelerinizle açıklayın.**

_Cevabınız:_


**- Projede bir cihaza komutlar neden tek tek sırayla veriliyor, hepsi bir seferde verilmiyor? Bu tasarım kararının sebebi ne olabilir?**

_Cevabınız:_


**- Cihaz komutu aldıktan sonra uzun süre cevap vermezse sisteminiz ne yapmalı? Projede bu durumu ele alan mekanizmayı bulun.**

_Cevabınız:_



---

### c) Birden Fazla Sunucu (Multi-Instance)

**- Bir cihazın bekleyen komutu varken, iki instance aynı anda bu komutu okuyup işlemeye çalışırsa ne gibi bir sorun oluşur?**

_Cevabınız:_


**- Bu "yarış durumunu" (race condition) önlemek için ne tür bir mekanizma kullanırdınız? (İpucu: "lock" kavramını araştırın.)**

_Cevabınız:_


**- Projede `LOCK_KEY_PREFIX` sabitini bulun — nasıl bir kilitleme uygulanmış?**

_Cevabınız:_



---

### d) Sunucu Çökerse Ne Olur?

**- Komut kaydedildikten sonra sunucu çökerse komut kaybolur mu? Neden veya neden kaybolmaz?**

_Cevabınız:_


**- Projede sunucu başlangıcında çalışan bir "kurtarma" mekanizması var mı? (`loadPendingCommandsOnStartup` veya benzer bir metod arayın.)**

_Cevabınız:_


**- Komutlar hem Redis'e hem veritabanına yazılıyor — bunun sebebi ne olabilir?**

_Cevabınız:_



---

## İncelenmesi Gereken Dosyalar

| Dosya | Konum | Ne Bulacaksınız |
|-------|-------|-----------------|
| `RedisAppleCommandQueueServiceImpl.java` | `services/apple/command/` | Komut saklama, kilitleme, timeout temizleme |
| `AppleCheckinServiceImpl.java` | `services/enrollment/` | Cihazın sunucuya bağlanma akışı |
| `AppleCommand.java` | `domains/` | Komut entity'si — hangi alanlar var, status nasıl tutuluyor |
| `AgentPresenceService.java` | `services/agent/` | Cihazın online/offline durumu nasıl takip ediliyor |
