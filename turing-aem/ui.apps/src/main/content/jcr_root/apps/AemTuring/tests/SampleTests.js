new hobs.TestSuite("AemTuring Tests", {path:"/apps/AemTuring/tests/SampleTests.js", register: true})

    .addTestCase(new hobs.TestCase("Hello World component on english page")
        .navigateTo("/content/AemTuring/en.html")
        .asserts.location("/content/AemTuring/en.html", true)
        .asserts.visible(".helloworld", true)
    )

    .addTestCase(new hobs.TestCase("Hello World component on french page")
        .navigateTo("/content/AemTuring/fr.html")
        .asserts.location("/content/AemTuring/fr.html", true)
        .asserts.visible(".helloworld", true)
    );
