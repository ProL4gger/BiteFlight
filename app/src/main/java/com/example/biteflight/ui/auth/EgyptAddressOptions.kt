package com.example.biteflight.ui.auth

/** Major cities, districts, and commonly used address areas by governorate. */
internal object EgyptAddressOptions {
    val areasByGovernorate = mapOf(
        "Alexandria" to listOf(
            "Abis", "Agami", "Amreya", "Asafra", "Azarita", "Borg El Arab", "Camp Caesar",
            "Gleem", "Ibrahimia", "Kafr Abdo", "Kom El Dikka", "Louran", "Mamoura",
            "Mandara", "Miami", "Montaza", "Raml Station", "Ras El Tin", "Rushdy",
            "San Stefano", "Sidi Bishr", "Sidi Gaber", "Smouha", "Sporting", "Stanley", "Victoria"
        ),
        "Assiut" to listOf(
            "Abnoub", "Abu Tig", "Al Badari", "Al Fath", "Al Ghanayem", "Al Qusiya",
            "Assiut City", "Dayrout", "Manfalut", "New Assiut", "Sahel Selim", "Sodfa"
        ),
        "Aswan" to listOf(
            "Abu Simbel", "Aswan City", "Daraw", "Edfu", "Kalabsha", "Kom Ombo",
            "Nasr El Nuba", "New Aswan", "Sebaiya"
        ),
        "Beheira" to listOf(
            "Abu Hummus", "Abu El Matamir", "Damanhour", "Delengat", "Edku", "El Mahmoudia",
            "El Rahmaniya", "Etay El Baroud", "Hosh Eissa", "Kafr El Dawwar", "Kom Hamada",
            "New Nubariya", "Rosetta (Rashid)", "Shubrakhit", "Wadi El Natrun"
        ),
        "Beni Suef" to listOf(
            "Al Fashn", "Al Wasta", "Beba", "Beni Suef City", "Ihnasya", "Nasser",
            "New Beni Suef", "Samasta"
        ),
        "Cairo" to listOf(
            "15 May City", "Abbasia", "Ain Shams", "Al Azbakeya", "Al Basatin", "Al Darb Al Ahmar",
            "Al Khalifa", "Al Marg", "Al Matariya", "Al Moski", "Al Sharabiya", "Al Wayli",
            "Badr City", "Bab El Shaariya", "Boulaq Abu El Ela", "Dar El Salam", "Downtown Cairo",
            "El Nozha", "El Shorouk", "El Tebin", "El Zawya El Hamra", "El Zeitoun", "Garden City",
            "Hadayek El Qobba", "Heliopolis", "Helwan", "Maadi", "Madinaty", "Mokattam",
            "Nasr City", "New Cairo", "New Heliopolis", "Old Cairo", "Qasr El Nil", "Rehab City",
            "Rod El Farag", "Sayeda Zeinab", "Shubra", "Tora", "Zamalek"
        ),
        "Dakahlia" to listOf(
            "Aga", "Belqas", "Beni Ebeid", "Dikirnis", "El Gamaliya", "El Kurdi",
            "El Manzala", "El Matareya", "El Senbellawein", "Gamasa", "Mansoura",
            "Meet Salsil", "Minyet El Nasr", "Mit Ghamr", "Nabaroh", "New Mansoura",
            "Sherbin", "Talkha", "Temay El Amdid"
        ),
        "Damietta" to listOf(
            "Damietta City", "El Zarka", "Ezbet El Borg", "Faraskur", "Kafr El Battikh",
            "Kafr Saad", "Meet Abu Ghaleb", "New Damietta", "Ras El Bar", "El Sarw", "El Rawda"
        ),
        "Fayoum" to listOf(
            "Abshaway", "Fayoum City", "Itsa", "New Fayoum", "Sennoures",
            "Tamiya", "Tunis Village", "Youssef El Seddik"
        ),
        "Gharbia" to listOf(
            "Basyoun", "El Mahalla El Kubra", "Kafr El Zayat", "Qutur", "Samannoud",
            "Santa", "Tanta", "Zefta"
        ),
        "Giza" to listOf(
            "6th of October City", "Agouza", "Al Ayat", "Al Badrashin", "Al Hawamdeya",
            "Al Omraniya", "Al Warraq", "Atfih", "Bahariya Oasis", "Bulaq El Dakrour",
            "Dahab Island", "Dokki", "Faisal", "Giza City", "Hadayek El Ahram",
            "Hadayek October", "Haram", "Imbaba", "Kerdasa", "Kit Kat", "Manshiyet El Qanater",
            "Mohandessin", "Osim", "Saf", "Saqqara", "Sheikh Zayed City"
        ),
        "Ismailia" to listOf(
            "Abu Suweir", "El Qantara East", "El Qantara West", "Fayed", "Ismailia City",
            "New Ismailia", "Qassassin", "Tell El Kebir"
        ),
        "Kafr El Sheikh" to listOf(
            "Baltim", "Bella", "Desouk", "Fuwwah", "Hamoul", "Kafr El Sheikh City",
            "Metoubes", "Qallin", "Riyadh", "Sidi Salem"
        ),
        "Luxor" to listOf(
            "Al Bayadiya", "Al Qurna", "Al Tod", "Armant", "Esna", "Luxor City",
            "New Tiba", "Zinnia"
        ),
        "Matrouh" to listOf(
            "Al Dabaa", "Al Hamam", "El Alamein", "Marsa Matrouh", "New Alamein",
            "Sallum", "Sidi Abdel Rahman", "Sidi Barrani", "Siwa"
        ),
        "Minya" to listOf(
            "Abu Qurqas", "Adwa", "Beni Mazar", "Deir Mawas", "Maghagha", "Mallawi",
            "Matay", "Minya City", "New Minya", "Samalut"
        ),
        "Monufia" to listOf(
            "Ashmoun", "Bagour", "Berket El Sabaa", "Menouf", "Quesna", "Sadat City",
            "Shibin El Kom", "Sers El Layyan", "Tala", "El Shohada"
        ),
        "New Valley" to listOf("Balat", "Baris", "Dakhla", "Farafra", "Kharga", "Mut"),
        "North Sinai" to listOf(
            "Al Arish", "Bir El Abd", "Hasana", "Nakhl", "Rafah", "Sheikh Zuweid"
        ),
        "Port Said" to listOf(
            "Al Arab", "Al Dawahi", "Al Ganoub", "Al Manakh", "Al Sharq", "Al Zohour",
            "Port Fouad", "Port Said City"
        ),
        "Qalyubia" to listOf(
            "Al Khanka", "Al Qanater El Khayreya", "Al Obour", "Banaha", "Kafr Shukr",
            "Qaha", "Qalyub", "Shibin El Qanater", "Shubra El Kheima", "Tukh", "Khosous"
        ),
        "Qena" to listOf(
            "Abu Tesht", "Al Waqf", "Dishna", "Farshut", "Nag Hammadi", "Naqada",
            "New Qena", "Qena City", "Qift", "Qus"
        ),
        "Red Sea" to listOf(
            "El Gouna", "El Quseir", "Halayeb", "Hurghada", "Marsa Alam", "Ras Gharib",
            "Safaga", "Sahl Hasheesh", "Shalateen"
        ),
        "Sharqia" to listOf(
            "10th of Ramadan City", "Abu Hammad", "Abu Kabir", "Al Husseiniya", "Awlad Saqr",
            "Belbeis", "Deyerb Negm", "El Ebrahimiya", "El Qurein", "Faqous", "Hihya",
            "Kafr Saqr", "Mashtoul El Souk", "Minya El Qamh", "New Salhia", "San El Hagar",
            "Zagazig"
        ),
        "Sohag" to listOf(
            "Akhmim", "Al Balyana", "Al Maragha", "Al Minshah", "Dar El Salam", "Girga",
            "Juhayna", "New Sohag", "Saqultah", "Sohag City", "Tahta", "Tima"
        ),
        "South Sinai" to listOf(
            "Abu Rudeis", "Abu Zenima", "Dahab", "El Tor", "Nuweiba", "Ras Sidr",
            "Saint Catherine", "Sharm El Sheikh", "Taba"
        ),
        "Suez" to listOf(
            "Ain Sokhna", "Al Arbaeen", "Attaka", "Faisal", "Ganayen", "Suez City"
        )
    )
}
