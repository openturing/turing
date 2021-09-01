import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { TurSNSearchDocument } from './model/sn-search-document.model';
import { TurSNSearch } from './model/sn-search.model';
import { TurSNSearchService } from './service/sn-search.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {
  private turSNSearchItems: Observable<TurSNSearch>;

  constructor(private turSNSearchService: TurSNSearchService, private router: Router) {
    this.turSNSearchItems = turSNSearchService.query("Sample");
  }

  getTurSNSearchItems(): Observable<TurSNSearch> {
    return this.turSNSearchItems;
  }

  ngOnInit(): void {
  }

  title = 'sn';
}
