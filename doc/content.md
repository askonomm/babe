# Content

There are two content file types in Babe: Markdown content file and Selmer template file. Markdown content files must end with an `.md` suffix and Selmer template files must end with `.sel` suffix. 

## What's the difference between Markdown and Selmer?

### Markdown

A Markdown content file comes in this format:

```markdown
---
meta_key: value
another_meta_key: another value
---

Markdown content to be parsed into HTML goes here.
```

As you can see it takes YAML-like metadata at the top, in-between three hyphen characters, and has the actual Markdown content below that.

Markdown content files are great for formatted content like blog posts or pages, and for content where you need key-value pairs of information.

**Note:** A markdown content file will be compiled into an index.html file with the name of the Markdown file becoming the directory that contains the index.html file. For example; hello-world.md would end up being /hello-world/index.html. 

### Selmer

A Selmer template file is basically just an HTML file. Thereby it can contain whatever you want, which can be a whole HTML document, enriched with a 
custom design and so forth. But, unlike regular HTML, it comes with superpowers. Namely, [Selmer](https://github.com/yogthos/Selmer) templating. Selmer templating allows you to 
create content with complex logic based on data available with what you can get pretty creative and create things like RSS feeds, sitemaps and so forth.

**Note:** A Selmer content file will be compiled into whatever is the name of the template, minus the .sel suffix. For example: sitemap.xml.sel would end up being sitemap.xml. Similarly, custom-page.html.sel would end up being custom-page.html. The only exception where this does not apply is with the `layout.sel` file, which is used as a layout for Markdown files and the home page.

**Additional note:** To get syntax highlighting for .sel files I recommend you configure your editor/IDE to think of .sel files as Twig templates, or as Django templates, as they are pretty much the same.

